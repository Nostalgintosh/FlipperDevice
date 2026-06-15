package com.a11.hackerboard

/**
 * The horizontally-scrolling strip above the keys. Each chip inserts [text]
 * (a [CURSOR] marker, if present, positions the caret). These are everyday
 * shell tokens and command names — the stuff that's painful to peck out on a
 * stock phone keyboard.
 */
data class Snippet(val label: String, val text: String)

object Snippets {
    val list: List<Snippet> = listOf(
        // pipes / redirection / expansion
        Snippet("|", " | "),
        Snippet("~/", "~/"),
        Snippet("./", "./"),
        Snippet("../", "../"),
        Snippet("&&", " && "),
        Snippet("||", " || "),
        Snippet(">", " > "),
        Snippet(">>", " >> "),
        Snippet("2>&1", " 2>&1"),
        Snippet("\$()", "\$($CURSOR)"),
        Snippet("\${}", "\${$CURSOR}"),
        Snippet("*", "*"),
        Snippet("-", "-"),
        Snippet("--", "--"),
        Snippet("#", "#"),

        // common command names (standard sysadmin / networking tools)
        Snippet("sudo", "sudo "),
        Snippet("ssh", "ssh "),
        Snippet("scp", "scp "),
        Snippet("cd", "cd "),
        Snippet("ls -la", "ls -la "),
        Snippet("cat", "cat "),
        Snippet("grep", "grep "),
        Snippet("chmod +x", "chmod +x "),
        Snippet("curl", "curl "),
        Snippet("wget", "wget "),
        Snippet("nmap", "nmap "),
        Snippet("nc", "nc "),
        Snippet("python3", "python3 "),
        Snippet("git", "git "),
        Snippet("ping", "ping "),
        Snippet("ip a", "ip a"),
        Snippet("ss -tulpn", "ss -tulpn"),

        // addresses / ports / encodings
        Snippet("127.0.0.1", "127.0.0.1"),
        Snippet("0.0.0.0", "0.0.0.0"),
        Snippet("localhost", "localhost"),
        Snippet(":port", ":"),
        Snippet("/24", "/24"),
        Snippet("0x", "0x"),
        Snippet("\\x", "\\x"),
    )
}
