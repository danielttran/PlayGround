// Deterministic child-process fixture for production UCI adapter error handling.
using System;
using System.IO;
using System.Threading;
class FakeUciEngine {
    static void Main() {
        string mode = File.ReadAllText("mode.txt").Trim();
        File.WriteAllText("started.txt", "started");
        string line;
        while ((line = Console.ReadLine()) != null) {
            File.AppendAllText("commands.txt", line + "\n");
            if (line == "uci") {
                if (mode == "eof") return;
                Console.WriteLine("uciok");
            } else if (line == "isready") Console.WriteLine("readyok");
            else if (line.StartsWith("go ")) {
                if (mode == "hang") { Thread.Sleep(60000); return; }
                if (mode == "critical") { Console.WriteLine("info string CRITICAL ERROR fixture failure"); continue; }
                if (mode == "draw") Console.WriteLine("info depth 1 score cp 0 nodes 0 pv");
                else if (mode != "incomplete") Console.WriteLine("info depth 1 score cp 20 nodes 1 pv b0c2");
                if (mode == "stream") {
                    Console.WriteLine("info depth 2 score cp 100 lowerbound nodes 2 pv a0a1");
                    Console.WriteLine("info depth 2 score cp 30 nodes 3 pv h0g2");
                }
                Console.WriteLine("bestmove " + (mode == "illegal" ? "a0a9" : mode == "stream" ? "h0g2" : "b0c2"));
            } else if (line == "quit") return;
        }
    }
}
