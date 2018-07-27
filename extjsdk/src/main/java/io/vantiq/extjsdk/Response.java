package io.vantiq.extjsdk;

import java.util.Map;
import java.util.HashMap;

/**
 *
 * This class provides the core requirements for Extension Source response messages.  All fields are public
 * so that it can be easily serialized into JSON for use in our WebSocket protocol.
 *
 * <p>Fields:
 * <ul>
 * <li>status -- Http Status code</li>
 * <li>headers -- {@code Map<String, String>} (key, value) pairs</li>
 * <li>body -- any serializable object</li>
 * <li>contentType -- set to (and always used as) application/json.</li>
 *</ul>
 * <p>Typical Usage:
 * <p>This code is generally used to create a Response object to send back the VANTIQ server.  Generally, this may be
 *   done using inline constructors.
 *
 *<pre>
 *   Response response = new Response()
 *                          .code(200) // HTTP OK
 *                          .addHeader("X-Reply-To", replyAddress)
 *                          .body(someJsonObject)
 *   }
 *   // Now do something with the {@code response}.
 *</pre>
 */

public class Response {
    public int status;
    public Map<String,String> headers;
    public Object body;
    public String contentType;

    public Response() {
        headers = new HashMap<String, String>();
        body = null;
        status = 0;
        contentType = "application/json; charset=utf-8";
    }

    public Response status(int statusCode) {
        status = statusCode;
        return this;
    }

    public Response body(Object body) {
        this.body = body;
        return this;
    }

    public Response addHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }
    
    public String getHeader(String name) {
        return this.headers.get(name);
    }
    
    public Object getBody() {
        return this.body;
    }
    
    public int getStatus() {
        return this.status;
    }
    
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public String toString() {
        return this.asMap().toString();
    }
    
    public static Response fromMap(Map<String,Object> m) {
        Response resp = new Response();
        if (m.get("body") instanceof Object) resp.body = m.get("body");
        if (m.get("status") instanceof Integer) resp.status = (int) m.get("status");
        if (m.get("headers") instanceof Map) resp.headers = (Map) m.get("headers");
        if (m.get("contentType") instanceof String) resp.contentType = (String) m.get("contentType");
        
        return resp;
    }
    
    public Map<String,Object> asMap() {
        Map<String, Object> m = new HashMap<>();
        
        if (this.body != null) m.put("body", this.body);
        if (this.status != 0) m.put("status", this.status);
        if (this.headers != null) m.put("headers", this.headers);
        if (this.contentType != null) m.put("contentType", this.contentType);
        
        return m;
    }
}
