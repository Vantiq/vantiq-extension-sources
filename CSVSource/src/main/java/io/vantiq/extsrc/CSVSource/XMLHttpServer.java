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

import com.sun.net.httpserver.*;

public class XMLHttpServer {
    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    HttpServer server;
    ThreadPoolExecutor threadPoolExecutor;
    public ExtensionWebSocketClient oClient = null; 
    public String context1 = "/alarm";
    public int port = 8001;
    public String IPListenAddress = "localhost";

    public Boolean Stop(){
        
        if (server != null){
            server.stop(0);
        }
        return true; 
    }

    public Boolean Start() {
        int maxActiveTasks = 4;
        int maxQueuedTasks = 10;

        threadPoolExecutor = new ThreadPoolExecutor(maxActiveTasks, maxActiveTasks, 0l, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(maxQueuedTasks));

        try {
            server = HttpServer.create(new InetSocketAddress(IPListenAddress, port), 0);
            server.createContext(context1, new MyHttpHandler());
            server.setExecutor(threadPoolExecutor);
            server.start();
            log.info(" Server started on Context "+context1+" IP:"+IPListenAddress+":"+ port );

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
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
                
                InputStreamReader isr =  new InputStreamReader(httpExchange.getRequestBody(),"utf-8");
                BufferedReader br = new BufferedReader(isr);
                
                String line = null; 
                String value = "";
                while ((line = br.readLine()) != null ){
                    value += line; 
                }
                log.error(value);

                try {
                    Boolean extendedLogging = true; 
                    JSONObject json = XML.toJSONObject(value); // converts xml to json
                    String jsonPrettyPrintString = json.toString(); // json pretty print
                    if (extendedLogging){
                        log.info(jsonPrettyPrintString);
                    }
                    
                    ArrayList<Map<String, String>> file = new ArrayList<Map<String, String>>();
                    Map<String, String> lineValues = new HashMap<String, String>();
                    lineValues.put("xml", jsonPrettyPrintString);
                    file.add(lineValues);
    
                    CSVReader.sendNotification("TCP","XML", 0, file, oClient);
                } catch (JSONException je) {
                    log.error("Convert2Json failed", je);
                    requestParamValue = je.toString(); 
                }
    


            } else {
                log.error("RX unexpected message method "+method);
            }

            handleResponse(httpExchange, requestParamValue);

        }

        private void handleResponse(HttpExchange httpExchange, String requestParamValue) throws IOException {

            OutputStream outputStream = httpExchange.getResponseBody();

            StringBuilder htmlBuilder = new StringBuilder();

            htmlBuilder.append("<html>").
                    append("<body>").
                    append("<h1>").
                    append("Hello ")
                    .append(requestParamValue)
                    .append("</h1>")
                    .append("</body>")
                    .append("</html>");

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
