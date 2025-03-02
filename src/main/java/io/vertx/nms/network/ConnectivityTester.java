package io.vertx.nms.network;

public class ConnectivityTester
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
