package co.mcsniper.mcsniper.sniper;

import co.mcsniper.mcsniper.MCSniper;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResponseLog {

    private Queue<Response> responses;
    private AtomicBoolean success;
    private MCSniper handler;
    private AbstractSniper sniper;

    public ResponseLog(MCSniper handler, AbstractSniper sniper) {
        this.responses = new ConcurrentLinkedQueue<Response>();
        this.success = new AtomicBoolean();
        this.handler = handler;
        this.sniper = sniper;
    }

    public void addResponse(Response response) {
        this.responses.add(response);
    }

    public void setSuccess(boolean success) {
        this.success.set(success);
    }

    public boolean isSuccess() {
        return this.success.get();
    }

    public void pushLog() {
        StringBuilder sb = new StringBuilder();

        sb.append("Server Name: ").append(this.handler.getServerName()).append("\n");
        sb.append("Server Host: ").append(this.handler.getServerIP()).append("\n");
        sb.append("Program Version: ").append(this.handler.getVersion()).append("\n\n");

        sb.append("Snipe Name: ").append(this.sniper.getName()).append("\n");
        sb.append("Snipe Date: ").append(this.sniper.getDate()).append("\n");
        sb.append("Snipe Result: ").append(this.isSuccess() ? "Success" : "Fail").append("\n");
        sb.append("Proxy Count: ").append(this.sniper.getProxyCount()).append("\n");
        sb.append("Proxy Instances: ").append(this.sniper.getProxyInstances()).append("\n");
        sb.append("Proxy Offset: ").append(this.sniper.isUseFunction() ? "Function-Generated" : this.sniper.getProxyOffset()).append("\n");

        List<Response> validResponses = new ArrayList<Response>(this.responses.size());

        while (this.responses.size() > 0) {
            validResponses.add(this.responses.poll());
        }

        Collections.sort(validResponses, new Comparator<Response>() {
            public int compare(Response o1, Response o2) {
                if (o1.getWebOffset() == o2.getWebOffset()) {
                    return Long.compare(o1.getOffset(), o2.getOffset());
                } else {
                    return Long.compare(o1.getWebOffset(), o2.getWebOffset());
                }
            }
        });

        sb.append("\n########## Response Log ##########\n\n");

        DecimalFormat timeFormat = new DecimalFormat("+###,###ms;-###,###ms");

        JSONObject responses = new JSONObject();
        JSONObject configuration = new JSONObject();

        configuration.put("proxies", this.sniper.getProxyCount())
                .put("instances", this.sniper.getProxyInstances())
                .put("offset", this.sniper.isUseFunction() ? 0 : this.sniper.getProxyOffset());

        sb.append("Proxy                         Proxy Offset   Response Date Server Date   HTTP Code    Response\n\n");
        for (Response response : validResponses) {
            sb.append(StringUtils.rightPad(response.getProxy().toString(), 30, " "));
            sb.append("[ @").append(StringUtils.rightPad(timeFormat.format(response.getProxyOffset()), 9)).append(" ] ");
            sb.append("[ ").append(StringUtils.rightPad(timeFormat.format(response.getOffset()), 9)).append(" ] [ ");
            sb.append(StringUtils.rightPad(timeFormat.format(response.getWebOffset()), 9)).append(" ] ");
            sb.append("( HTTP ").append(response.getStatusCode()).append(" ) ");
            sb.append(response.getResponse()).append("\n");

            String parsedResponse = response.getResponse().contains("Exception: ") ? response.getResponse().substring(0, response.getResponse().indexOf("Exception: ") + 9) : response.getResponse();
            if (responses.has(parsedResponse)) {
                responses.increment(parsedResponse);
            } else {
                responses.put(parsedResponse, 1);
            }
        }

        this.handler.getMySQL().pushLog(
                this.handler.getServerName(),
                this.sniper.getSnipeId(),
                this.sniper.getName(),
                MCSniper.DATE_FORMAT.format(this.sniper.getDate()),
                this.success.get() ? 1 : 0,
                sb.toString(),
                responses,
                configuration
        );
    }

}
