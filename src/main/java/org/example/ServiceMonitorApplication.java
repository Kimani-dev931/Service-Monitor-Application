package org.example;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;


import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;



import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;


import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class ServiceMonitorApplication {
    private static boolean isMonitoringActive = false;
    private static ScheduledExecutorService executor;


    public static void main(String[] args) {
        String serviceFile = "services.json";
        List<Service> services ;

        if (serviceFile.endsWith(".csv")) {
            services = readServicesFromCSV(serviceFile);
        } else if (serviceFile.endsWith(".json")) {
            services = readServicesFromJSON(serviceFile);
        } else if (serviceFile.endsWith(".xml")) {
            services = readServicesFromXML(serviceFile);
        } else if (serviceFile.endsWith(".yaml") || serviceFile.endsWith(".yml")) {
            services = readServicesFromYAML(serviceFile);
        } else if (serviceFile.endsWith(".ini")) {
            services = readServicesFromINI(serviceFile);
        } else {
            System.out.println("Unsupported file format.");
            return;
        }


        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Enter command ('exit' to quit):");
            String command = scanner.nextLine();

            if ("exit".equalsIgnoreCase(command)) {
                if (executor != null && !executor.isShutdown()) {
                    executor.shutdown();
                }
                break;
            }

            String[] commandParts = command.split(" ");
            switch (commandParts[0].toLowerCase()) {
                case "sky-monitor":
                    handleSkyMonitorCommands(commandParts, services);
                    break;
                default:
                    System.out.println("Invalid command.");
                    break;
            }
        }
        scanner.close();

    }
    private static void handleSkyMonitorCommands(String[] commandParts, List<Service> services) {
        if (commandParts.length < 2) {
            System.out.println("Invalid command.");
            return;
        }


        if (!isMonitoringActive && !"start".equalsIgnoreCase(commandParts[1])) {
            System.out.println("The sky-monitor application must be started first.");
            return;
        }

        switch (commandParts[1].toLowerCase()) {
            case "start":
                startMonitoring(services);
                break;
            case "stop":
                stopMonitoring();
                break;
            case "service":
                if (commandParts.length == 3 && "list".equalsIgnoreCase(commandParts[2])) {
                    listServices(services);
                } else {
                    System.out.println("Invalid service command.");
                }
                break;
            case "application":
            case "server":
                if (commandParts.length == 4 && "status".equalsIgnoreCase(commandParts[2])) {
                    int id = Integer.parseInt(commandParts[3]);
                    Service service = findServiceById(services, id);
                    if (service != null) {
                        if ("application".equalsIgnoreCase(commandParts[1])) {
                            logServiceStatus(service);
                        } else {
                            logServerStatus(service);
                        }
                    } else {
                        System.out.println("Service with ID " + id + " not found.");
                    }
                } else {
                    System.out.println("Invalid command for application/server.");
                }
                break;
            default:
                System.out.println("Invalid sky-monitor command.");
                break;
        }
    }
    private static void startMonitoring(List<Service> services) {
        if (isMonitoringActive) {
            System.out.println("Monitoring is already active.");
            return;
        }

        isMonitoringActive = true;
        executor = Executors.newScheduledThreadPool(services.size());
        initializeLogging(services);
        initializeArchiving(services);
        System.out.println("Sky-monitor application is active.");
    }


    private static void stopMonitoring() {
        if (!isMonitoringActive) {
            System.out.println("Monitoring is not active.");
            return;
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        isMonitoringActive = false;
        System.out.println("Monitoring stopped.");
    }
    private static void listServices(List<Service> services) {
        System.out.println("Listing all services:");
        for (Service service : services) {
            boolean isServerUp = checkServerStatus(service);
            boolean isServiceUp = checkServiceStatus(service);
            Date currentTime = new Date();

            String serverStatus = isServerUp ? "UP" : "DOWN";
            String serviceStatus = isServiceUp ? "UP" : "DOWN";

            System.out.println("ID: " + service.getid() + ", Name: " + service.getServiceName() +
                    ", Server Status: " + serverStatus + " (as of " + currentTime + ")" +
                    ", Application Status: " + serviceStatus + " (as of " + currentTime + ")");
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
    private static List<Service> readServicesFromXML(String xmlFile) {
        List<Service> services = new ArrayList<>();
        try {
            JAXBContext context = JAXBContext.newInstance(ServiceList.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            ServiceList serviceList = (ServiceList) unmarshaller.unmarshal(new File(xmlFile));
            services = serviceList.getServices();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return services;
    }
    private static List<Service> readServicesFromJSON(String jsonFile) {
        List<Service> services = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        Path pathToFile = Paths.get(jsonFile);

        try {
            services = mapper.readValue(Files.newInputStream(pathToFile), new TypeReference<List<Service>>(){});
        } catch (IOException e) {
            e.printStackTrace();
        }

        return services;
    }
    private static List<Service> readServicesFromINI(String iniFile) {
        List<Service> services = new ArrayList<>();
        Configurations configs = new Configurations();
        try {
            INIConfiguration config = configs.ini(new File(iniFile));
            Set<String> sectionNames = config.getSections();
            for (String section : sectionNames) {
                Service service = new Service();
                service.setId(config.getInt(section + ".id"));
                service.setServiceName(config.getString(section + ".serviceName"));
                service.setServiceHost(config.getString(section + ".serviceHost"));
                service.setServicePort(config.getInt(section + ".servicePort"));
                service.setServiceResourceURI(config.getString(section + ".serviceResourceURI"));
                service.setServiceMethod(config.getString(section + ".serviceMethod"));
                service.setExpectedTelnetResponse(config.getString(section + ".expectedTelnetResponse"));
                service.setExpectedRequestResponse(config.getString(section + ".expectedRequestResponse"));
                service.setMonitoringInterval(config.getInt(section + ".monitoringInterval"));
                service.setMonitoringIntervalTimeUnit(config.getString(section + ".monitoringIntervalTimeUnit"));

                service.setEnableFileLogging(config.getString(section + ".enableFileLogging"));
                service.setFileLoggingInterval(config.getString(section + ".fileLoggingInterval"));
                service.setEnableLogsArchiving(config.getString(section + ".enableLogsArchiving"));
                service.setLogArchivingIntervals(config.getString(section + ".logArchivingIntervals"));

                services.add(service);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return services;
    }


    private static List<Service> readServicesFromYAML(String yamlFile) {
        Yaml yaml = new Yaml(new Constructor(ServiceList.class));
        List<Service> services = new ArrayList<>();
        try (InputStream inputStream = new FileInputStream(new File(yamlFile))) {
            ServiceList data = yaml.load(inputStream);
            if (data != null) {
                services = data.getServices();
            }
        } catch (IOException e) {
            e.printStackTrace();
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


        String enableFileLogging = metadata[10];
        String fileLoggingInterval = metadata[11];
        String enableLogsArchiving = metadata[12];
        String logArchivingIntervals = metadata[13];

        return new Service(id, serviceName, serviceHost, servicePort, serviceResourceURI, serviceMethod,
                expectedTelnetResponse, expectedRequestResponse, monitoringInterval,
                monitoringIntervalTimeUnit, enableFileLogging, fileLoggingInterval,
                enableLogsArchiving, logArchivingIntervals);
    }



    private static boolean checkServerStatus(Service service) {
        try {
            InetAddress addr = InetAddress.getByName(service.getServiceHost());
            int port = service.getServicePort();
            SocketAddress sockaddr = new InetSocketAddress(addr, port);
            Socket sock = new Socket();
            int timeoutMs = 2000;
            sock.connect(sockaddr, timeoutMs);
            sock.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    private static void logServerStatus(Service service) {
        boolean isServerUp = checkServerStatus(service);
        String serverStatus = isServerUp ? "UP" : "DOWN";
        System.out.println(new Date() + " - Server hosting " + service.getServiceName() + " is " + serverStatus);
        if (!isServerUp) {
            System.out.println("Error monitoring " + service.getServiceName());
        }
    }

    private static boolean checkServiceStatus(Service service) {

        boolean isServerUp = checkServerStatus(service);

        if (isServerUp) {
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

                URL url = new URL("https://" + service.getServiceHost() + service.getServiceResourceURI());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(service.getServiceMethod());
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int status = conn.getResponseCode();
                return (status >= 200 && status < 300);
            } catch (Exception e) {
                return false;
            }
        } else {
            return false;
        }
    }
    private static void logServiceStatus(Service service) {
        boolean isServiceUp = checkServiceStatus(service);
        String serviceStatus = isServiceUp ? "UP" : "DOWN";
        System.out.println(new Date() + " " + service.getServiceName() + " - Service is " + serviceStatus);
    }
    private static Service findServiceById(List<Service> services, int id) {
        for (Service service : services) {
            if (service.getid() == id) {
                return service;
            }
        }
        return null;
    }


    private static void initializeLogging(List<Service> services) {
        Path loggingDirectory = Paths.get("logging");
        try {
            Files.createDirectories(loggingDirectory);
            for (Service service : services) {
                if ("Yes".equalsIgnoreCase(service.getEnableFileLogging())) {
                    Path serviceDirectory = loggingDirectory.resolve(service.getServiceName().replaceAll("\\s+", "_"));
                    Files.createDirectories(serviceDirectory);

                    Path appStatusDir = serviceDirectory.resolve("application_status");
                    Path serverStatusDir = serviceDirectory.resolve("server_status");
                    Files.createDirectories(appStatusDir);
                    Files.createDirectories(serverStatusDir);

                    scheduleLogging(appStatusDir, serverStatusDir, service);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void scheduleLogging(Path appStatusDir, Path serverStatusDir, Service service) {
        long fileCreationInterval = convertToInterval(service.getFileLoggingInterval());
        long logEntryInterval = convertToIntervalInSeconds(service.getMonitoringInterval(), service.getMonitoringIntervalTimeUnit());

        executor.scheduleAtFixedRate(() -> logStatus(appStatusDir, "application", checkServiceStatus(service), fileCreationInterval),
                0, logEntryInterval, TimeUnit.SECONDS);
        executor.scheduleAtFixedRate(() -> logStatus(serverStatusDir, "server", checkServerStatus(service), fileCreationInterval),
                0, logEntryInterval, TimeUnit.SECONDS);
    }




    private static final Map<String, Long> lastLogFileCreationTimes = new HashMap<>();

    private static final String LAST_LOG_TIME_DIR = "last_logging_time";

    private static void writeLastLogTimestamp(String serviceName, String type, long timestamp) {
        Path lastLogTimeDir = Paths.get(LAST_LOG_TIME_DIR);
        try {
            Files.createDirectories(lastLogTimeDir);
            Path timestampFile = lastLogTimeDir.resolve(serviceName + "_" + type + ".timestamp");

            try (BufferedWriter writer = Files.newBufferedWriter(timestampFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(String.valueOf(timestamp));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static long readLastLogTimestamp(String serviceName, String type) {
        Path timestampFile = Paths.get(LAST_LOG_TIME_DIR, serviceName + "_" + type + ".timestamp");
        if (Files.exists(timestampFile)) {
            try {
                String timestampStr = new String(Files.readAllBytes(timestampFile));
                return Long.parseLong(timestampStr);
            } catch (IOException | NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return System.currentTimeMillis();
    }
    private static void logStatus(Path directory, String type, boolean status, long fileCreationInterval) {
        String serviceName = directory.getParent().getFileName().toString();
        String key = serviceName + "_" + type;

        long lastTimestamp = lastLogFileCreationTimes.getOrDefault(key, readLastLogTimestamp(serviceName, type));
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastTimestamp >= fileCreationInterval) {
            lastTimestamp = currentTime;
            lastLogFileCreationTimes.put(key, lastTimestamp);
        }

        writeLastLogTimestamp(serviceName, type, lastTimestamp);

        String logFileTimestamp = new SimpleDateFormat("yyyyMMdd_HHmm").format(new Date(lastTimestamp));
        Path logFile = directory.resolve(logFileTimestamp + "_" + type + ".log");
        String logEntry = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + " - " + type.toUpperCase() + " is " + (status ? "UP" : "DOWN");

        try {
            Files.write(logFile, Collections.singletonList(logEntry), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }





    private static long convertToIntervalInSeconds(long interval, String timeUnit) {
        switch (timeUnit.toLowerCase()) {
            case "minutes":
                return TimeUnit.MINUTES.toSeconds(interval);
            case "seconds":
                return interval;
            default:
                return TimeUnit.MINUTES.toSeconds(10);
        }
    }

    private static long convertToInterval(String interval) {
        switch (interval.toLowerCase()) {
            case "hourly":
                return TimeUnit.HOURS.toMillis(1);
            case "daily":
                return TimeUnit.DAYS.toMillis(1);
            default:
                return TimeUnit.MINUTES.toMillis(10);
        }
    }



    private static void initializeArchiving(List<Service> services) {

        File mainLogsDir = new File("logs");
        if (!mainLogsDir.exists()) {
            mainLogsDir.mkdirs();
        }

        for (Service service : services) {
            if ("Yes".equalsIgnoreCase(service.getEnableLogsArchiving())) {

                File logsDir = new File(mainLogsDir, service.getServiceName());
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }

                long delay = calculateArchiveInterval(service.getLogArchivingIntervals());

                executor.scheduleWithFixedDelay(() -> {
                    archiveLogs(service.getServiceName(), logsDir);
                }, delay, delay, TimeUnit.MILLISECONDS);
            }
        }
    }


    private static void archiveLogs(String serviceName, File logsDir) {

        if (logsDir.exists() && logsDir.isDirectory()) {
            compressToZip(serviceName, logsDir);
        }
    }

    private static void compressToZip(String serviceName, File directory) {
        String zipFileName = serviceName + "_logs_" + System.currentTimeMillis() + ".zip";
        File zipFile = new File(directory.getParent(), zipFileName);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File file : directory.listFiles()) {
                if (file.isFile()) {
                    ZipEntry zipEntry = new ZipEntry(file.getName());
                    zos.putNextEntry(zipEntry);
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    zos.write(bytes, 0, bytes.length);
                    zos.closeEntry();
                    file.delete();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static long calculateArchiveInterval(String interval) {
        if ("Weekly".equalsIgnoreCase(interval)) {
            return TimeUnit.DAYS.toMillis(7);
        } else if ("Seconds".equalsIgnoreCase(interval)) {
            return TimeUnit.SECONDS.toMillis(1);
        }


        return 0;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Service {
        @XmlElement(name = "id")
        private int id;
        @XmlElement(name = "serviceName")
        private String serviceName;
        @XmlElement(name = "serviceHost")
        private String serviceHost;

        @XmlElement(name = "servicePort")
        private int servicePort;
        @XmlElement(name = "serviceResourceURI")
        private String serviceResourceURI;
        @XmlElement(name = "serviceMethod")
        private String serviceMethod;

        @XmlElement(name = "expectedTelnetResponse")
        private String expectedTelnetResponse;
        @XmlElement(name = "expectedRequestResponse")
        private String expectedRequestResponse;
        @XmlElement(name = "monitoringInterval")
        private int monitoringInterval;
        @XmlElement(name = "monitoringIntervalTimeUnit")
        private String monitoringIntervalTimeUnit;

        @XmlElement(name = "enableFileLogging")
        private String enableFileLogging;

        @XmlElement(name = "fileLoggingInterval")
        private String fileLoggingInterval;

        @XmlElement(name = "enableLogsArchiving")
        private String enableLogsArchiving;

        @XmlElement(name = "logArchivingIntervals")
        private String logArchivingIntervals;


        public Service() {
        }

        public Service(int id, String serviceName, String serviceHost, int servicePort, String serviceResourceURI,
                       String serviceMethod, String expectedTelnetResponse, String expectedRequestResponse,
                       int monitoringInterval, String monitoringIntervalTimeUnit, String enableFileLogging,
                       String fileLoggingInterval, String enableLogsArchiving, String logArchivingIntervals) {
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
            this.enableFileLogging = enableFileLogging;
            this.fileLoggingInterval = fileLoggingInterval;
            this.enableLogsArchiving = enableLogsArchiving;
            this.logArchivingIntervals = logArchivingIntervals;
        }

        // Getters
        public String getServiceName() {
            return serviceName;
        }

        public int getid() {
            return id;
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

        public int getServicePort() {
            return servicePort;
        }

        public String getExpectedTelnetResponse() {
            return expectedTelnetResponse;
        }

        public String getExpectedRequestResponse() {
            return expectedRequestResponse;
        }

        // Setters
        public void setId(int id) {
            this.id = id;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public void setServiceHost(String serviceHost) {
            this.serviceHost = serviceHost;
        }

        public void setServicePort(int servicePort) {
            this.servicePort = servicePort;
        }

        public void setServiceResourceURI(String serviceResourceURI) {
            this.serviceResourceURI = serviceResourceURI;
        }

        public void setServiceMethod(String serviceMethod) {
            this.serviceMethod = serviceMethod;
        }

        public void setExpectedTelnetResponse(String expectedTelnetResponse) {
            this.expectedTelnetResponse = expectedTelnetResponse;
        }

        public void setExpectedRequestResponse(String expectedRequestResponse) {
            this.expectedRequestResponse = expectedRequestResponse;
        }

        public void setMonitoringInterval(int monitoringInterval) {
            this.monitoringInterval = monitoringInterval;
        }

        public void setMonitoringIntervalTimeUnit(String monitoringIntervalTimeUnit) {
            this.monitoringIntervalTimeUnit = monitoringIntervalTimeUnit;
        }

        public String getEnableFileLogging() {
            return enableFileLogging;
        }

        public void setEnableFileLogging(String enableFileLogging) {
            this.enableFileLogging = enableFileLogging;
        }

        public String getFileLoggingInterval() {
            return fileLoggingInterval;
        }

        public void setFileLoggingInterval(String fileLoggingInterval) {
            this.fileLoggingInterval = fileLoggingInterval;
        }

        public String getEnableLogsArchiving() {
            return enableLogsArchiving;
        }

        public void setEnableLogsArchiving(String enableLogsArchiving) {
            this.enableLogsArchiving = enableLogsArchiving;
        }

        public String getLogArchivingIntervals() {
            return logArchivingIntervals;
        }

        public void setLogArchivingIntervals(String logArchivingIntervals) {
            this.logArchivingIntervals = logArchivingIntervals;
        }
    }

    @XmlRootElement(name = "services")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ServiceList {
        @XmlElement(name = "service")
        private List<Service> services;


        public List<Service> getServices() {
            return services;
        }

        public void setServices(List<Service> services) {
            this.services = services;
        }
    }

}