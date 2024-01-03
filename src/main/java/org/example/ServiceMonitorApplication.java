package org.example;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class ServiceMonitorApplication {

    public static void main(String[] args) {
        String csvFile = "services.csv";
        List<Service> services = readServicesFromCSV(csvFile);

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(services.size());
        for (Service service : services) {
            Runnable task = () -> monitorService(service);

            long interval = service.getMonitoringInterval();
            TimeUnit timeUnit = service.getMonitoringIntervalTimeUnit().equalsIgnoreCase("Minutes") ? TimeUnit.MINUTES : TimeUnit.SECONDS;

            executor.scheduleWithFixedDelay(task, 0, interval, timeUnit);
        }
    }

    private static List<Service> readServicesFromCSV(String csvFile) {
        List<Service> services = new ArrayList<>();
        Path pathToFile = Paths.get(csvFile);

        try (BufferedReader br = Files.newBufferedReader(pathToFile)) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] attributes = line.split(",");
                Service service = createService(attributes);
                services.add(service);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return services;
    }

    private static Service createService(String[] metadata) {
        int id = Integer.parseInt(metadata[0]);
        String serviceName = metadata[1];
        String serviceHost = metadata[2];
        int servicePort = Integer.parseInt(metadata[3]);
        String serviceResourceURI = metadata[4];
        String serviceMethod = metadata[5];
        String expectedTelnetResponse = metadata[6];
        String expectedRequestResponse = metadata[7];
        int monitoringInterval = Integer.parseInt(metadata[8]);
        String monitoringIntervalTimeUnit = metadata[9];
        return new Service(id, serviceName, serviceHost, servicePort, serviceResourceURI, serviceMethod, expectedTelnetResponse, expectedRequestResponse, monitoringInterval, monitoringIntervalTimeUnit);
    }

    private static void monitorService(Service service) {




        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            InetAddress addr = InetAddress.getByName(service.getServiceHost());
            int port = service.getServicePort();
            SocketAddress sockaddr = new InetSocketAddress(addr, port);
            Socket sock = new Socket();
            int timeoutMs = 2000;
            sock.connect(sockaddr, timeoutMs);
            boolean isServerUp=true;
            sock.close();

            if (isServerUp) {
                URL url = new URL("https://" + service.getServiceHost() + service.getServiceResourceURI());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(service.getServiceMethod());
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int status = conn.getResponseCode();
                boolean isServiceUp = (status >= 200 && status < 300);
                logStatus(service, isServiceUp,isServerUp);
            }
        } catch (Exception e) {
            System.out.println("Error monitoring " + service.getServiceName() + ": " + e);
            logStatus(service, false,false);
        }
    }

    private static void logStatus(Service service, boolean isServiceUp,boolean isServerUp) {
        String servicestatus = isServiceUp ? "UP" : "DOWN";
        String serverstatus = isServerUp ? "UP" : "DOWN";
        if (isServerUp) {
            System.out.println(new Date()+ " " + service.getServiceName() + " - Server is  "+serverstatus + " and Service is " + servicestatus);
        }else{
            System.out.println(new Date() + " - Server hosting " + service.getServiceName() + " is " + serverstatus);
        }
    }

    static class Service {
        private int id;
        private String serviceName;
        private String serviceHost;
        private int servicePort;
        private String serviceResourceURI;
        private String serviceMethod;
        private String expectedTelnetResponse;
        private String expectedRequestResponse;
        private int monitoringInterval;
        private String monitoringIntervalTimeUnit;

        public Service(int id, String serviceName, String serviceHost, int servicePort, String serviceResourceURI, String serviceMethod, String expectedTelnetResponse, String expectedRequestResponse, int monitoringInterval, String monitoringIntervalTimeUnit) {
            this.id = id;
            this.serviceName = serviceName;
            this.serviceHost = serviceHost;
            this.servicePort = servicePort;
            this.serviceResourceURI = serviceResourceURI;
            this.serviceMethod = serviceMethod;
            this.expectedTelnetResponse = expectedTelnetResponse;
            this.expectedRequestResponse = expectedRequestResponse;
            this.monitoringInterval = monitoringInterval;
            this.monitoringIntervalTimeUnit = monitoringIntervalTimeUnit;
        }


        public String getServiceName() {
            return serviceName;
        }

        public int getMonitoringInterval() {
            return monitoringInterval;
        }

        public String getMonitoringIntervalTimeUnit() {
            return monitoringIntervalTimeUnit;
        }
        public String getServiceHost() {
            return serviceHost;
        }

        public String getServiceResourceURI() {
            return serviceResourceURI;
        }

        public String getServiceMethod() {
            return serviceMethod;
        }

        public int getServicePort(){return servicePort;}

    }
}
