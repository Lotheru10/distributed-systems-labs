#!/usr/bin/env python3
import argparse
import os
import queue
import signal
import subprocess
import sys
import threading
import tkinter as tk
from tkinter import messagebox

from kazoo.client import KazooClient
from kazoo.exceptions import NoNodeError
from kazoo.protocol.states import KazooState


class GuiNotifier:
    def __init__(self):
        self.queue = queue.Queue()

        self.root = tk.Tk()
        self.root.title("ZooKeeper /a watcher")
        self.root.geometry("520x260")

        self.label = tk.Label(
            self.root,
            text="Czekam na utworzenie /a...",
            font=("Arial", 14),
            padx=20,
            pady=10,
        )
        self.label.pack(fill="x")

        self.tree_button = tk.Button(
            self.root,
            text="Pokaż drzewo /a",
            command=self._show_tree_clicked,
        )
        self.tree_button.pack(pady=5)

        self.tree_text = tk.Text(self.root, height=8, width=60)
        self.tree_text.pack(padx=10, pady=10, expand=True, fill="both")

        self.on_close = None
        self.on_show_tree = None

        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.root.after(100, self._process_queue)

    def set_count(self, count: int) -> None:
        self.queue.put(("set", count, []))

    def notify_child_count(self, count: int, added_children: list[str]) -> None:
        self.queue.put(("notify", count, added_children))

    def set_waiting(self) -> None:
        self.queue.put(("waiting",))

    def close(self) -> None:
        self.queue.put(("close",))

    def _show_tree_clicked(self) -> None:
        if self.on_show_tree is None:
            return

        tree = self.on_show_tree()
        self.tree_text.delete("1.0", tk.END)
        self.tree_text.insert(tk.END, tree)

    def _update_label(self, count: int) -> None:
        self.label.config(text=f"Aktualna liczba potomków /a: {count}")

    def _process_queue(self) -> None:
        try:
            while True:
                item = self.queue.get_nowait()
                kind = item[0]

                if kind == "set":
                    _, count, _ = item
                    self._update_label(count)

                elif kind == "notify":
                    _, count, added_children = item
                    self._update_label(count)

                    self.root.deiconify()
                    self.root.lift()

                    added = ", ".join(added_children)
                    messagebox.showinfo(
                        "ZooKeeper: dodano potomka do /a",
                        f"Dodano: {added}\nAktualna liczba potomków /a: {count}",
                        parent=self.root,
                    )

                elif kind == "waiting":
                    self.label.config(text="Czekam na utworzenie /a...")
                    self.tree_text.delete("1.0", tk.END)

                elif kind == "close":
                    if self.on_close is not None:
                        self.on_close()
                    self.root.destroy()
                    return

        except queue.Empty:
            pass

        self.root.after(100, self._process_queue)

    def run(self) -> None:
        self.root.mainloop()


class ZkAWatcher:
    def __init__(
        self,
        hosts: str,
        app_command: list[str],
        notifier: GuiNotifier,
        path: str = "/a",
    ):
        self.hosts = hosts
        self.path = path
        self.app_command = app_command
        self.notifier = notifier

        self.zk = KazooClient(hosts=hosts)
        self.zk.add_listener(self._zk_listener)

        self.process: subprocess.Popen | None = None
        self.lock = threading.RLock()
        self.stop_event = threading.Event()

        self.a_exists = False
        self.known_children: set[str] | None = None

        self.children_watch_generation = 0
        self.children_watch = None

    def start(self) -> None:
        self.zk.start()

        @self.zk.DataWatch(self.path)
        def watch_a(data, stat, event):
            return self._on_a_changed(data, stat, event)

    def stop(self) -> None:
        self.stop_event.set()

        with self.lock:
            self.a_exists = False
            self.children_watch_generation += 1
            self._stop_external_app()

        self.zk.stop()
        self.zk.close()

    def _zk_listener(self, state: KazooState) -> None:
        if state == KazooState.CONNECTED:
            print("[zk] Połączono z ensemble ZooKeeper.")
        elif state == KazooState.SUSPENDED:
            print("[zk] Połączenie zawieszone; czekam na ponowne połączenie...")
        elif state == KazooState.LOST:
            print("[zk] Sesja utracona.")

    def _on_a_changed(self, data, stat, event) -> bool:
        exists_now = stat is not None

        with self.lock:
            if self.stop_event.is_set():
                return False

            if exists_now and not self.a_exists:
                self.a_exists = True
                self.known_children = None

                print(f"[watch] {self.path} istnieje / zostało utworzone.")
                self._start_external_app()
                self._install_children_watch()

            elif not exists_now and self.a_exists:
                self.a_exists = False
                self.known_children = None
                self.children_watch_generation += 1

                print(f"[watch] {self.path} zostało usunięte.")
                self.notifier.set_waiting()
                self._stop_external_app()

        return True

    def _install_children_watch(self) -> None:
        self.children_watch_generation += 1
        generation = self.children_watch_generation

        self.children_watch = self.zk.ChildrenWatch(
            self.path,
            func=lambda children, event: self._on_children_changed(
                children,
                event,
                generation,
            ),
            send_event=True,
        )

    def _on_children_changed(self, children, event, generation: int) -> bool:
        current_children = set(children or [])
        count = len(current_children)

        with self.lock:
            if (
                self.stop_event.is_set()
                or not self.a_exists
                or generation != self.children_watch_generation
            ):
                return False

            if self.known_children is None:
                self.known_children = current_children
                self.notifier.set_count(count)
                return True

            added_children = sorted(current_children - self.known_children)
            self.known_children = current_children

        if added_children:
            print(
                f"[watch] Dodano dzieci do {self.path}: {added_children}. "
                f"Aktualna liczba: {count}"
            )
            self.notifier.notify_child_count(count, added_children)
        else:
            self.notifier.set_count(count)

        return True

    def _start_external_app(self) -> None:
        if not self.app_command:
            print("[app] Brak komendy aplikacji zewnętrznej.")
            return

        if self.process is not None and self.process.poll() is None:
            print("[app] Aplikacja już działa.")
            return

        try:
            if os.name == "nt":
                self.process = subprocess.Popen(self.app_command)
            else:
                self.process = subprocess.Popen(
                    self.app_command,
                    start_new_session=True,
                )

            print(f"[app] Uruchomiono: {' '.join(self.app_command)}")

        except FileNotFoundError:
            print(
                f"[app] Nie znaleziono programu: {self.app_command[0]}",
                file=sys.stderr,
            )
        except Exception as exc:
            print(f"[app] Nie udało się uruchomić aplikacji: {exc}", file=sys.stderr)

    def _stop_external_app(self) -> None:
        if self.process is None:
            return

        if self.process.poll() is not None:
            self.process = None
            return

        print("[app] Zatrzymuję aplikację zewnętrzną...")

        try:
            if os.name == "nt":
                self.process.terminate()
            else:
                os.killpg(os.getpgid(self.process.pid), signal.SIGTERM)

            self.process.wait(timeout=5)

        except subprocess.TimeoutExpired:
            if os.name == "nt":
                self.process.kill()
            else:
                os.killpg(os.getpgid(self.process.pid), signal.SIGKILL)

        finally:
            self.process = None

    def render_tree(self) -> str:
        if not self.zk.exists(self.path):
            return f"{self.path} nie istnieje."

        lines = [self.path]
        self._render_subtree(self.path, prefix="", lines=lines)
        return "\n".join(lines)

    def _render_subtree(self, path: str, prefix: str, lines: list[str]) -> None:
        try:
            children = sorted(self.zk.get_children(path))
        except NoNodeError:
            return

        for index, child in enumerate(children):
            is_last = index == len(children) - 1
            connector = "└── " if is_last else "├── "
            lines.append(prefix + connector + child)

            child_path = path.rstrip("/") + "/" + child
            child_prefix = prefix + ("    " if is_last else "│   ")
            self._render_subtree(child_path, child_prefix, lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Watcher /a dla ZooKeeper z uruchamianiem zewnętrznej aplikacji GUI."
    )

    parser.add_argument(
        "--hosts",
        default="127.0.0.1:2181",
        help=(
            "Lista serwerów ZooKeeper, np. "
            "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183"
        ),
    )

    parser.add_argument(
        "--path",
        default="/a",
        help="Ścieżka obserwowanego znode, domyślnie /a",
    )

    parser.add_argument(
        "app_command",
        nargs=argparse.REMAINDER,
        help="Komenda aplikacji graficznej, np. -- xeyes albo -- gedit",
    )

    args = parser.parse_args()

    if args.app_command and args.app_command[0] == "--":
        args.app_command = args.app_command[1:]

    if not args.app_command:
        parser.error(
            "Podaj komendę aplikacji po --, np. "
            "python zk_a_watcher.py --hosts ... -- xeyes"
        )

    return args


def main() -> None:
    args = parse_args()

    gui = GuiNotifier()
    monitor = ZkAWatcher(args.hosts, args.app_command, gui, path=args.path)

    gui.on_close = monitor.stop
    gui.on_show_tree = monitor.render_tree

    monitor.start()
    gui.run()


if __name__ == "__main__":
    main()