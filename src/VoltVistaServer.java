import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;

/** Native Java launcher and HTTP API for the VoltVista desktop dashboard. */
public final class VoltVistaServer {
    private final DataManager database = new DataManager();
    private final Path appData = Path.of(System.getProperty("user.home"), "Library", "Application Support", "VoltVista");
    private final Path activeCsv = appData.resolve("ev_data.csv");

    public static void main(String[] args) throws Exception { new VoltVistaServer().start(args); }

    private void start(String[] args) throws Exception {
        Files.createDirectories(appData);
        Path requested = argument(args, "--data");
        if (requested != null) Files.copy(requested, activeCsv, StandardCopyOption.REPLACE_EXISTING);
        if (!Files.exists(activeCsv)) copyBundled("/data/ev_data.csv", activeCsv);
        database.load(activeCsv);

        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/meta", this::meta);
        server.createContext("/api/records", this::records);
        server.createContext("/api/stats", this::stats);
        server.createContext("/api/group", this::group);
        server.createContext("/api/save", this::save);
        server.createContext("/api/import", this::importCsv);
        server.createContext("/", this::staticFile);
        server.setExecutor(Executors.newFixedThreadPool(6));
        server.start();

        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        System.out.println("VoltVista is running at " + uri);
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(uri);
    }

    private void meta(HttpExchange ex) throws IOException {
        require(ex, "GET");
        sendJson(ex, 200, "{\"header\":" + strings(database.header())
            + ",\"states\":" + strings(database.states())
            + ",\"uses\":" + strings(database.primaryUses())
            + ",\"rows\":" + database.size()
            + ",\"dirty\":" + database.dirty() + "}");
    }

    private void records(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) { add(ex); return; }
        if ("DELETE".equals(ex.getRequestMethod())) { delete(ex); return; }
        require(ex, "GET");
        Map<String,String> q = query(ex);
        if (q.containsKey("counties")) {
            sendJson(ex, 200, strings(database.counties(q.get("counties")))); return;
        }
        List<DataManager.Record> rows = filtered(q);
        int page = Math.max(1, integer(q.get("page"), 1));
        int pageSize = Math.min(250, Math.max(10, integer(q.get("pageSize"), 50)));
        int from = Math.min(rows.size(), (page - 1) * pageSize), to = Math.min(rows.size(), from + pageSize);
        StringBuilder json = new StringBuilder("{\"total\":").append(rows.size()).append(",\"page\":")
            .append(page).append(",\"pageSize\":").append(pageSize).append(",\"records\":[");
        for (int i=from;i<to;i++) { if(i>from)json.append(','); DataManager.Record r=rows.get(i);
            json.append("{\"id\":").append(quote(r.id)).append(",\"values\":").append(strings(r.values)).append('}'); }
        sendJson(ex, 200, json.append("]}").toString());
    }

    private void stats(HttpExchange ex) throws IOException {
        require(ex, "GET"); DataManager.Stats s=database.stats(filtered(query(ex)));
        sendJson(ex,200,"{\"records\":"+s.records()+",\"bev\":"+s.bev()+",\"phev\":"+s.phev()
            +",\"evTotal\":"+s.evTotal()+",\"vehicles\":"+s.vehicles()+",\"weightedEvPercent\":"
            +number(s.weightedEvPercent())+",\"topState\":"+quote(s.topState())+",\"topCounty\":"+quote(s.topCounty())+"}");
    }

    private void group(HttpExchange ex) throws IOException {
        require(ex,"GET");Map<String,String>q=query(ex);Map<String,Double>groups=database.groupPercent(filtered(q),q.getOrDefault("by","state"),12);
        StringBuilder j=new StringBuilder("[");int i=0;for(var e:groups.entrySet()){if(i++>0)j.append(',');j.append("{\"label\":").append(quote(e.getKey())).append(",\"value\":").append(number(e.getValue())).append('}');}
        sendJson(ex,200,j.append(']').toString());
    }

    private void add(HttpExchange ex) throws IOException {
        Map<String,String> form=form(ex);String[] h=database.header(),values=new String[h.length];
        for(int i=0;i<h.length;i++)values[i]=form.getOrDefault("c"+i,"");
        try{DataManager.Record r=database.add(values);sendJson(ex,201,"{\"ok\":true,\"id\":"+quote(r.id)+"}");}
        catch(IllegalArgumentException e){sendJson(ex,400,"{\"error\":"+quote(e.getMessage())+"}");}
    }

    private void delete(HttpExchange ex) throws IOException {
        String id=query(ex).getOrDefault("id","");boolean removed=database.delete(id);
        sendJson(ex,removed?200:404,"{\"ok\":"+removed+"}");
    }

    private void save(HttpExchange ex) throws IOException {
        require(ex,"POST");try{database.save();sendJson(ex,200,"{\"ok\":true,\"path\":"+quote(activeCsv.toString())+"}");}
        catch(IOException e){sendJson(ex,500,"{\"error\":"+quote(e.getMessage())+"}");}
    }

    private void importCsv(HttpExchange ex) throws IOException {
        require(ex,"POST");byte[] bytes=ex.getRequestBody().readAllBytes();
        if(bytes.length>25_000_000){sendJson(ex,413,"{\"error\":\"File is larger than 25 MB.\"}");return;}
        Path incoming=appData.resolve("import.csv.tmp");Files.write(incoming,bytes);
        try{database.load(incoming);database.save();Files.move(incoming,activeCsv,StandardCopyOption.REPLACE_EXISTING);database.load(activeCsv);sendJson(ex,200,"{\"ok\":true,\"rows\":"+database.size()+"}");}
        catch(Exception e){Files.deleteIfExists(incoming);sendJson(ex,400,"{\"error\":"+quote(e.getMessage())+"}");}
    }

    private List<DataManager.Record> filtered(Map<String,String>q){return database.query(q.get("state"),q.get("county"),q.get("use"),q.get("search"));}

    private void staticFile(HttpExchange ex) throws IOException {
        String path=ex.getRequestURI().getPath();if(path.equals("/"))path="/index.html";
        if(path.contains("..")){send(ex,400,"text/plain","Bad path".getBytes());return;}
        String resource="/web"+path;try(InputStream in=getClass().getResourceAsStream(resource)){
            if(in==null){send(ex,404,"text/plain","Not found".getBytes());return;}
            String type=path.endsWith(".css")?"text/css":path.endsWith(".js")?"application/javascript":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".png")?"image/png":"text/html";
            send(ex,200,type,in.readAllBytes());
        }
    }

    private void copyBundled(String resource,Path target)throws IOException{try(InputStream in=getClass().getResourceAsStream(resource)){if(in==null)throw new IOException("Bundled dataset missing");Files.copy(in,target);}}
    private static Path argument(String[]args,String name){for(int i=0;i<args.length-1;i++)if(args[i].equals(name))return Path.of(args[i+1]);return null;}
    private static void require(HttpExchange ex,String method)throws IOException{if(!method.equals(ex.getRequestMethod())){sendJson(ex,405,"{\"error\":\"Method not allowed\"}");throw new IOException("method");}}
    private static Map<String,String> query(HttpExchange ex){return decode(ex.getRequestURI().getRawQuery());}
    private static Map<String,String> form(HttpExchange ex)throws IOException{return decode(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));}
    private static Map<String,String> decode(String raw){Map<String,String>m=new HashMap<>();if(raw==null||raw.isBlank())return m;for(String p:raw.split("&")){String[]kv=p.split("=",2);m.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),URLDecoder.decode(kv.length>1?kv[1]:"",StandardCharsets.UTF_8));}return m;}
    private static int integer(String s,int fallback){try{return Integer.parseInt(s);}catch(Exception e){return fallback;}}
    private static String number(double x){return String.format(Locale.ROOT,"%.4f",x);}
    private static String strings(Collection<String>v){return strings(v.toArray(String[]::new));}
    private static String strings(String[]v){StringBuilder j=new StringBuilder("[");for(int i=0;i<v.length;i++){if(i>0)j.append(',');j.append(quote(v[i]));}return j.append(']').toString();}
    private static String quote(String s){if(s==null)return"null";return"\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    private static void sendJson(HttpExchange ex,int status,String json)throws IOException{send(ex,status,"application/json; charset=utf-8",json.getBytes(StandardCharsets.UTF_8));}
    private static void send(HttpExchange ex,int status,String type,byte[]body)throws IOException{ex.getResponseHeaders().set("Content-Type",type);ex.getResponseHeaders().set("Cache-Control","no-store");ex.getResponseHeaders().set("X-Content-Type-Options","nosniff");ex.sendResponseHeaders(status,body.length);try(OutputStream out=ex.getResponseBody()){out.write(body);}}
}
