package io.vertx.nms.network;

public class ConnectivityTester
{

    // Pings the given IP address to check its reachability.
    //@param ipAddress The IP address to ping.
    //@return true if the IP is reachable, false otherwise.
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
