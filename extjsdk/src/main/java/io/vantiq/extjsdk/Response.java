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
    public Map headers;
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
}
