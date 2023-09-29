
//imports
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

//Maggie McComas 434 Network Design Project 1

/**
 * MyWebServer class is responsible for creating the welcome and connection socket as well as starting the worker thread
 * and handling the user input
 */
public final class MyWebServer {

    /**
     * @param argv
     * @throws Exception
     * Main function that takes in user input for the webserver
     */
    public static void main(String argv[]) throws Exception {
        int port = 6789;
        String root = "/evaluationWeb";
        String[] relativePath;

        if (argv.length < 2) {
            System.out.println(
                    "Incorrect input please try again. Usage: java MyWebServer.java <<port_number>> ~/evaluationWeb");
            System.exit(1);
        }
        port = Integer.parseInt(argv[0]);
        root = argv[1];
        relativePath = root.split("/");
        root = "/" + relativePath[3];

        ServerSocket welcomeSocket = new ServerSocket(port);
        System.out.println("Maggie's Server listening on port " + port);

        while (true) {
            Socket connectionSocket = welcomeSocket.accept();

            new workerThread(connectionSocket, root).start();

        }
    }
}

/**
 * Worker thread that creates HTTPRequest object and calls its parseRequest method in order to parse client request
 * Also creates HTTPResponse object and calls it's requestHadnler method in order to output the correct response for the client request
 */
class workerThread extends Thread {

    Socket socket;
    String root;

    /**
     * @param connectionSocket
     * @param rootLocation
     * Constructor for worker thread which handles connection requests and responses
     */
    workerThread(Socket connectionSocket, String rootLocation) {
        socket = connectionSocket;
        root = rootLocation;
    }

    @Override
    public void run() {
        HTTPRequest request = new HTTPRequest(socket, root);

        try {
            request.parseRequest();
           
            HTTPResponse response = new HTTPResponse(request, socket);

            response.requestHandler(root);

        } catch (Exception e) {
            System.out.println("Issue with the thread.");
        }
    }
}

/**
 * HTTPResponse class hadnles the output of a client request 
 */
final class HTTPResponse {

    private HTTPRequest request;
    private String command;
    private String path;
    private Date ifModified;
    private String lastMod;
    private int status;
    private String statusLine;
    private String body;
    private long length;
    private Socket connectionSocket;
    private String currentDate;

    /**
     * @param clientRequest
     * @param socket
     * Constructor for HTTPResponse which takes in input from clientRequest parameter as well as the working socket
     */
    HTTPResponse(HTTPRequest clientRequest, Socket socket) {
        request = clientRequest;
        path = request.getPath();
        command = request.getCommand();
        ifModified = request.getIfModified();
        status = request.getStatusCode();
        connectionSocket = socket;
    }

    /**
     * @param filePath
     * Function which handles the clinet request and outputs the correct headers and responses to the requests
     */
    public void requestHandler(String filePath) {
        try {
            DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
            File file = null;

            if (status == 200) {
                Calendar cal = Calendar.getInstance();
                Date date = cal.getTime();
                SimpleDateFormat curDate = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
                curDate.setTimeZone(TimeZone.getTimeZone("EST"));
                currentDate = curDate.format(date);

                try {
                    file = new File("." + path);

                    if (!file.exists() || !file.isFile()) {
                        status = 404;
                    } else {
                        long lastModified = file.lastModified();
                        lastMod = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
                                .format(lastModified);

                        length = file.length();

                        if (ifModified != null) {
                            Date modDate;
                            try {
                                modDate = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
                                        .parse(lastMod);

                                if (modDate.before(ifModified)) {
                                    status = 304;
                                }
                            } catch (ParseException e) {
                                System.out.println("Parse eror.");
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("FileinputStream error.");
                }
            }

            statusLine = "HTTP/1.1 ";

            switch (status) {
                case 304:
                    statusLine += "304 Not Modified\r\n";
                    break;

                case 400:
                    statusLine += "400 Bad Request\r\n";
                    break;

                case 404:
                    statusLine += "404 Not Found\r\n";
                    break;

                case 501:
                    statusLine += "501 Not Implemented\r\n";
                    break;

                default:
                    statusLine += "200 OK\r\n";
            }

            if (status != 200) {
                outToClient.writeBytes(statusLine);
                body = "\r\n\r\n" + statusLine + "\r\n";
                outToClient.write(body.getBytes(), 0, body.length());

            } else {
                if (command.equals("GET")) {
                    if (file.exists()) {
                        String extension = request.getExtension();

                        if (extension == null) {
                            status = 404;
                            outToClient.writeBytes(statusLine);
                            body = "\r\n\r\n" + statusLine + "\r\n";
                            outToClient.write(body.getBytes(), 0, body.length());

                        } else {

                            byte[] fileData = Files.readAllBytes(file.toPath());

                            if (extension.contains("txt") || extension.contains("html") || extension.contains("xml")
                                    || extension.contains("css")) {

                                outToClient.write(("HTTP/1.1 200 OK\r\n").getBytes());
                                outToClient.write(("Content-Type: text/" + extension + "\r\n").getBytes());
                                outToClient.write(("Content-Length: " + fileData.length + "\r\n").getBytes());
                                outToClient.write(("\r\n").getBytes());
                                outToClient.write(fileData);

                            } else if (extension.contains("gif") || extension.contains("jpg")
                                    || extension.contains("png")) {

                                outToClient.write(("HTTP/1.1 200 OK\r\n").getBytes());
                                outToClient.write(("Content-Type: image/" + extension + "\r\n").getBytes());
                                outToClient.write(("Content-Length: " + fileData.length + "\r\n").getBytes());
                                outToClient.write(("\r\n").getBytes());
                                outToClient.write(fileData);

                            } else {

                                status = 501;
                                outToClient.writeBytes(statusLine);
                                body = "\r\n\r\n" + statusLine + "\r\n";
                                outToClient.write(body.getBytes(), 0, body.length());
                            }
                        }
                    }
                } else {

                    outToClient.writeBytes(statusLine);
                    outToClient.writeBytes("Date: " + currentDate + "\r\n");
                    outToClient.writeBytes("Server: Maggie's Server\r\n");
                    outToClient.writeBytes("Last-Modified: " + lastMod + "\r\n");
                    outToClient.writeBytes("Content-Length: " + length + "\r\n");

                }
            }
            outToClient.close();
            connectionSocket.close();

        } catch (IOException e) {
            System.out.println("Issue with sending data back to the client");
        }
    }
}

/**
 * HTTPRequest class parses through the client request and tokenizes important information to be used in HTTPResponse
 */
final class HTTPRequest {
    
    private int statusCode = 0;
    private String command = null;;
    private String path = null;;
    private String root = null;
    private Date ifModified = null;
    private Socket socket;
    private String requestLine = null;
    private String extension = null;

    /**
     * @param connectionSocket
     * @param rootPath
     * constructore for HTTPRequest which takes in the working socket and path
     */
    HTTPRequest(Socket connectionSocket, String rootPath) {
        statusCode = 200;
        path = rootPath;
        root = rootPath;
        socket = connectionSocket;
    }

    /**
     * @throws Exception
     * Function which parses through client request to get information in order to help with the HTTP response
     */
    public void parseRequest() throws Exception {

        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        requestLine = inFromClient.readLine();
        extension = findExtension(requestLine);

        StringTokenizer tokenizer = new StringTokenizer(requestLine);

        if (tokenizer.hasMoreTokens()) {

            command = tokenizer.nextToken();
            command = command.replace("\\s", "");
 
            if (!command.equals("GET") && !command.equals("HEAD")) {
                statusCode = 501;
                return;
            }
        } else {
            statusCode = 400;
            return;
        }

        if (tokenizer.hasMoreTokens()) {
            path = path + "" + tokenizer.nextToken();

            if (path.endsWith("/")) {
                path += "index.html";
                extension = "html";
            }

            if (!path.startsWith(root)) {
                statusCode = 400;
                return;
            }

        } else {
            statusCode = 400;
            return;
        }

        String headerLine = null;
        String dateString = null;

        while ((headerLine = inFromClient.readLine()).length() != 0) {
            if (headerLine.trim().toLowerCase().contains("if-modified-since")) {

                int indexOfSemi = headerLine.indexOf(":") + 1;
                dateString = headerLine.substring(indexOfSemi).trim();

                SimpleDateFormat HTTPDateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
                try {
                    ifModified = HTTPDateFormat.parse(dateString);

                } catch (ParseException e) {
                    statusCode = 400;
                    return;
                }
            }
        }
    }

    /**
     * @param request
     * @return substring that representions extension
     * Helper function to get the file extension of the requested file
     */
    private static String findExtension(String request) {
        int end = request.indexOf("HTTP");
        int start = request.indexOf(".") + 1;

        if (end == 0 || start > end) {
            return null;
        }
        return request.substring(start, end);
    }

    /**
     * @return String extension
     * helper function so HTTPResponse object can get extension value
     */
    public String getExtension() {
        return extension;
    }

    /**
     * @return int Status code
     * helper function so HTTPResponse can get the status code of the request
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * @return String command
     * helper function so HTTPResponse can get the command to determine whether it was implemented or not
     */
    public String getCommand() {
        return command;
    }

    /**
     * @return String path
     * helper function so HTTPResponse can get string representation of the path to the requested file
     */
    public String getPath() {
        return path;
    }

    /**
     * @return Date ifModified
     * helper function so HTTPResponse can get ifModified variable which will be null if the use didn't input if-modified-since or the date
     * that the user inputed
     */
    public Date getIfModified() {
        return ifModified;
    }

}
