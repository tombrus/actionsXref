package com.tombrus.xref;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.tombrus.persistentqueues.TimerThread;

public class Main implements Runnable {
    public static void main(String[] args) {
        new Main(args).run();
    }

    private String token;
    private Path   dir             = Paths.get("knowhow");
    private int    saveIntervalSec = 0;

    public Main(String[] args) {
        boolean nextIsToken        = false;
        boolean nextIsDir          = false;
        boolean nextIsSaveInterval = false;

        for (String a : args) {
            if (nextIsToken) {
                nextIsToken = false;
                token = a;
            } else if (nextIsDir) {
                nextIsDir = false;
                dir = Paths.get(a);
            } else if (nextIsSaveInterval) {
                nextIsSaveInterval = false;
                saveIntervalSec = Integer.parseUnsignedInt(a);
            } else if (a.equals("-token")) {
                nextIsToken = true;
            } else if (a.equals("-dir")) {
                nextIsDir = true;
            } else if (a.equals("-saveInterval")) {
                nextIsSaveInterval = true;
            } else {
                System.err.println("args:");
                System.err.println("    -dir           dir     # the dir where knowledge is stored         ; default is \"knowhow\"");
                System.err.println("    -token         token   # the OAUTH token to get more API throughput; default is empty (no token)");
                System.err.println("    -saveInterval  #sec    # the save interval in sec (>30 sec)        ; default is 0 (meaning no auto-save)");
                throw new Error("unrecogized argument: " + a);
            }
        }
    }

    @Override
    public void run() {
        try {
            Files.createDirectories(dir);
            Xref xref = new Xref(dir, token, saveIntervalSec);

            xref.unpause(false);
            while (!xref.isRateLimited()) {
                TimerThread.sleep_(1000);
                int numWithWf = xref.getGotWfsQueue().size();
                int numNoWf   = xref.getNowfQueue().size();
                if (0 < numWithWf + numNoWf) {
                    System.err.printf("============== found %12d repositories, %7d with actions (%2d %%)\n", numWithWf + numNoWf, numWithWf, (numWithWf * 100) / (numWithWf + numNoWf));
                }
            }
            xref.carefullSave(true);

            //TODO push results to git.
        } catch (Throwable t) {
            System.err.println("major error detected: " + t.getMessage());
            t.printStackTrace();
        }
    }
}
