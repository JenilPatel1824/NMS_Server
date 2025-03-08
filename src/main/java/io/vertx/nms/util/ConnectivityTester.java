package io.vertx.nms.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ConnectivityTester
{
    // Pings the given IP address using fping to check its reachability.
    // @param ipAddress The IP address to ping.
    // @return true if the IP is reachable, false otherwise.
    public static boolean ping(String ipAddress)
    {
        try
        {
            ProcessBuilder processBuilder = new ProcessBuilder("ping", "-c", "3", ipAddress);

            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;

            while ((line = reader.readLine()) != null)
            {
                if (line.contains("0% packet loss"))
                {
                    return true;
                }
            }
            process.waitFor();

            return false;
        }
        catch (Exception e)
        {
            System.out.println("Error executing fping: " + e.getMessage());

            return false;
        }
    }
}
