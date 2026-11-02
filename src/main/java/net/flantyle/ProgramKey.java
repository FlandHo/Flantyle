package net.flantyle;

public class ProgramKey {
    public static void getProgramRuntimeKey(String[] args) {
        String key = null;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-runKey=")) {
                key = args[i].substring("-runKey=".length());
                if (key.isEmpty()) {
                    throw new RuntimeException("[ERROR-CRASH] Run Key Error!");
                }
            }
        }

        if (key == null) {
            throw new RuntimeException("[ERROR-CRASH] Run Key Error!");
        }

        String[] KeyList = key.split("-", 3);
        if (KeyList.length != 3) {
            throw new RuntimeException("[ERROR-CRASH] Run Key Error!");
        }

        if (KeyList[0].length() == 5 && KeyList[1].length() == 5 && KeyList[2].equals("runKey")) {
            try {
                if (Integer.parseInt(KeyList[0]) % 7 == 0 && Integer.parseInt(KeyList[1]) % 8 == 0) {
                    System.out.println("[INFO-RUNKEY] Key is Verification successful");
                } else {
                    throw new RuntimeException("[ERROR-CRASH] Run Key Error!");
                }
            } catch (NumberFormatException e) {
                System.out.println("[WARN-KEY] Invalid number: " + e.getMessage());
                throw new RuntimeException("[ERROR-CRASH] Run Key Error!");
            }
        } else {
                throw new RuntimeException("[ERROR-CRASH] Run Key Error!");
        }
    }
}