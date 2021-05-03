package io.vantiq.extsrc.CSVSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONObject;
import org.json.XML;
import org.json.JSONException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vantiq.extjsdk.ExtensionWebSocketClient;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

/**
 * XMLHttpServer receive xml object from post HTTP request, convert it to JSON and notify vantiq source. 
 * congiguration enable IP - which control what is the scope the a meesages can send from, post to listen and the service path - define in the context 
 * value, for example IP: 0.0.0.0 - open to any IP connect request, Port 80 , Context /alarm means that the service is open to any message on port 80, which use the follwing url 
 * 
 * https://destinationIs:80/alarm
 */
public class XMLHttpServer {
    private Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    private HttpServer server;
    private ThreadPoolExecutor threadPoolExecutor;
    public ExtensionWebSocketClient oClient = null;
    public String context1 = "/alarm";
    public int port = 8001;
    public String ipListenAddress = "localhost";

    public Boolean stop() {

        if (server != null) {
            server.stop(0);
        }
        return true;
    }

    public Boolean start() {
        int maxActiveTasks = 4;
        int maxQueuedTasks = 10;

        threadPoolExecutor = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(maxQueuedTasks));

        try {
            server = HttpServer.create(new InetSocketAddress(ipListenAddress, port), 0);
            server.createContext(context1, new MyHttpHandler());
            server.setExecutor(threadPoolExecutor);
            server.start();
            log.info(" Server started on Context " + context1 + " IP:" + ipListenAddress + ":" + port);

        } catch (IOException e) {
            log.error("Create Server failuer", e);
        }

        return true;
    }

    private class MyHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            String requestParamValue = null;
            String method = httpExchange.getRequestMethod();

            if ("POST".equals(method)) {
                log.info("Accepting POST HTTP Request");
                InputStreamReader isr = new InputStreamReader(httpExchange.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);

                String line = null;
                String value = "";
                while ((line = br.readLine()) != null) {
                    value += line;
                }
                log.debug(value);

                try {
                    Boolean extendedLogging = true;
                    JSONObject json = XML.toJSONObject(value); // converts xml to json
                    String jsonPrettyPrintString = json.toString(); // json pretty print
                    if (extendedLogging) {
                        log.info(jsonPrettyPrintString);
                    }
                    ArrayList<Map<String, String>> file = new ArrayList<Map<String, String>>();
                    Map<String, String> lineValues = new HashMap<String, String>();
                    lineValues.put("xml", jsonPrettyPrintString);
                    file.add(lineValues);
                    CSVReader.sendNotification("TCP", "XML", 0, file, oClient);
                } catch (JSONException eje) {
                    log.error("Convert2Json failed", eje);
                    requestParamValue = eje.toString();
                } finally{
                    isr.close();
                    br.close();
                }

            } else {
                log.error("RX unexpected message method " + method);
            }
            handleResponse(httpExchange, requestParamValue);
        }

        private void handleResponse(HttpExchange httpExchange, String requestParamValue) throws IOException {

            OutputStream outputStream = httpExchange.getResponseBody();

            StringBuilder htmlBuilder = new StringBuilder(250);

            htmlBuilder.append("<html>").append("<body>").append("<h1>").append("Hello ").append(requestParamValue)
                    .append("</h1>").append("</body>").append("</html>");

            // encode HTML content

            String htmlResponse = StringEscapeUtils.escapeHtml4(htmlBuilder.toString());

            // this line is a must

            httpExchange.sendResponseHeaders(200, htmlResponse.length());
            outputStream.write(htmlResponse.getBytes());
            outputStream.flush();
            outputStream.close();

        }

    }
}
