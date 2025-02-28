package io.vertx.nms.util;

public class PingUtil
{
    public static boolean ping(String ipAddress)
    {
        try
        {
            ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "1", ipAddress);

            Process process = processBuilder.start();

            int returnCode = process.waitFor();

            return (returnCode == 0);
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
