import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/** ArrayList store + stable merge sort + HashMap indexes for the EV CSV. */
public final class DataManager {
    public static final String[] DEFAULT_HEADER = {"Date", "County", "State", "Vehicle Primary Use",
        "Battery Electric Vehicles (BEVs)", "Plug-In Hybrid Electric Vehicles (PHEVs)",
        "Electric Vehicle (EV) Total", "Non-Electric Vehicle Total", "Total Vehicles",
        "Percent Electric Vehicles"};

    public static final class Record {
        public final String id = UUID.randomUUID().toString();
        public final String[] values;
        Record(String[] values) { this.values = values; }
    }
    public record Stats(int records, long bev, long phev, long evTotal, long vehicles,
                        double weightedEvPercent, String topState, String topCounty) {}

    private final List<Record> data = new ArrayList<>();
    private final Map<String,List<Record>> stateIndex = new HashMap<>();
    private final Map<String,Map<String,List<Record>>> countyIndex = new HashMap<>();
    private String[] header = DEFAULT_HEADER.clone();
    private Path filePath;
    private boolean dirty;
    private int date, county, state, use, bev, phev, evTotal, nonEv, vehicles, percent;

    public synchronized void load(Path path) throws IOException {
        List<List<String>> rows;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            rows = parseCsv(reader);
        }
        if (rows.isEmpty()) throw new IOException("The CSV file is empty.");
        header = rows.get(0).toArray(String[]::new);
        resolveColumns();
        data.clear();
        for (int r = 1; r < rows.size(); r++) {
            if (rows.get(r).size() == 1 && rows.get(r).get(0).isBlank()) continue;
            String[] values = new String[header.length]; Arrays.fill(values, "");
            for (int c = 0; c < Math.min(values.length, rows.get(r).size()); c++)
                values[c] = rows.get(r).get(c).strip();
            data.add(new Record(values));
        }
        mergeSort(data, 0, data.size() - 1);
        rebuildIndexes(); filePath = path; dirty = false;
    }

    public synchronized void save() throws IOException {
        if (filePath == null) throw new IOException("No database file is open.");
        Path parent = filePath.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, "voltvista-", ".tmp");
        try (BufferedWriter out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            writeRow(out, header); for (Record record : data) writeRow(out, record.values);
        }
        try { Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(temp, filePath, StandardCopyOption.REPLACE_EXISTING); }
        dirty = false;
    }

    public synchronized Record add(String[] input) {
        String[] row = new String[header.length]; Arrays.fill(row, "");
        for (int i = 0; i < Math.min(input.length, row.length); i++) row[i] = input[i].strip();
        validate(row); row[state] = row[state].toUpperCase(Locale.ROOT);
        Record record = new Record(row); insertSorted(data, record); index(record); dirty = true; return record;
    }

    public synchronized boolean delete(String id) {
        boolean removed = data.removeIf(record -> record.id.equals(id));
        if (removed) { rebuildIndexes(); dirty = true; }
        return removed;
    }

    public synchronized List<Record> query(String stateValue, String countyValue,
                                            String useValue, String search) {
        String st = norm(stateValue), co = norm(countyValue), us = norm(useValue), needle = norm(search);
        List<Record> candidates = data;
        if (!st.isEmpty()) candidates = co.isEmpty()
            ? stateIndex.getOrDefault(st, List.of())
            : countyIndex.getOrDefault(st, Map.of()).getOrDefault(co, List.of());
        List<Record> out = new ArrayList<>();
        for (Record record : candidates) {
            if (!us.isEmpty() && !norm(get(record, use)).equals(us)) continue;
            if (!needle.isEmpty() && Arrays.stream(record.values).noneMatch(v -> norm(v).contains(needle))) continue;
            out.add(record);
        }
        return out;
    }

    public synchronized Stats stats(List<Record> rows) {
        long b=0,p=0,e=0,v=0; Map<String,long[]> states=new HashMap<>(), counties=new HashMap<>();
        for (Record r : rows) {
            long rb=num(get(r,bev)), rp=num(get(r,phev));
            long re=evTotal>=0?num(get(r,evTotal)):rb+rp, rv=num(get(r,vehicles));
            b+=rb; p+=rp; e+=re; v+=rv;
            accumulate(states,get(r,state),re,rv);
            accumulate(counties,get(r,county)+", "+get(r,state),re,rv);
        }
        return new Stats(rows.size(),b,p,e,v,v==0?0:100.0*e/v,top(states),top(counties));
    }

    public synchronized Map<String,Double> groupPercent(List<Record> rows, String group, int limit) {
        int column = switch(norm(group)) { case "county"->county; case "date"->date;
            case "primary use", "vehicle primary use"->use; default->state; };
        Map<String,long[]> totals=new HashMap<>();
        for (Record r:rows) {
            String key=get(r,column); if(column==county) key+=", "+get(r,state);
            long e=evTotal>=0?num(get(r,evTotal)):num(get(r,bev))+num(get(r,phev));
            accumulate(totals,key,e,num(get(r,vehicles)));
        }
        return totals.entrySet().stream().sorted((a,b)->Double.compare(pct(b.getValue()),pct(a.getValue())))
            .limit(Math.max(1,limit)).collect(Collectors.toMap(Map.Entry::getKey,e->pct(e.getValue()),
                (a,b)->a,LinkedHashMap::new));
    }

    public synchronized List<String> states(){ return new ArrayList<>(new TreeSet<>(stateIndex.keySet())); }
    public synchronized List<String> counties(String st){ return new ArrayList<>(new TreeSet<>(countyIndex.getOrDefault(norm(st),Map.of()).keySet())); }
    public synchronized List<String> primaryUses(){ Set<String>s=new TreeSet<>(); for(Record r:data)if(!get(r,use).isBlank())s.add(get(r,use)); return new ArrayList<>(s); }
    public synchronized String[] header(){return header.clone();}
    public synchronized int size(){return data.size();}
    public synchronized boolean dirty(){return dirty;}
    public synchronized Path filePath(){return filePath;}

    private void resolveColumns(){
        date=find("date"); county=find("county"); state=find("state"); use=find("vehicle primary use","primary use");
        bev=find("battery electric vehicles","bev"); phev=find("plug-in hybrid electric vehicles","phev");
        evTotal=find("electric vehicle (ev) total","ev total"); nonEv=find("non-electric vehicle total");
        vehicles=find("total vehicles"); percent=find("percent electric vehicles","percent");
        if(state<0||county<0||vehicles<0)throw new IllegalArgumentException("CSV needs State, County, and Total Vehicles columns.");
    }
    private int find(String... names){for(int i=0;i<header.length;i++)for(String n:names)if(norm(header[i]).equals(norm(n))||norm(header[i]).contains(norm(n)))return i;return -1;}
    private void validate(String[] row){
        if(get(row,state).isBlank())throw new IllegalArgumentException("State is required.");
        if(get(row,county).isBlank())throw new IllegalArgumentException("County is required.");
        for(int c:new int[]{bev,phev,evTotal,nonEv,vehicles})if(c>=0&&!get(row,c).isBlank()&&numStrict(get(row,c))<0)throw new IllegalArgumentException("Counts cannot be negative.");
        if(percent>=0&&!get(row,percent).isBlank()){double x=decStrict(get(row,percent));if(x<0||x>100)throw new IllegalArgumentException("EV percent must be 0–100.");}
    }
    private void rebuildIndexes(){stateIndex.clear();countyIndex.clear();for(Record r:data)index(r);}
    private void index(Record r){String st=norm(get(r,state)),co=norm(get(r,county));stateIndex.computeIfAbsent(st,k->new ArrayList<>()).add(r);countyIndex.computeIfAbsent(st,k->new HashMap<>()).computeIfAbsent(co,k->new ArrayList<>()).add(r);}
    private void insertSorted(List<Record> list,Record r){int lo=0,hi=list.size();while(lo<hi){int mid=(lo+hi)>>>1;if(compare(list.get(mid),r)>=0)lo=mid+1;else hi=mid;}list.add(lo,r);}
    private void mergeSort(List<Record> a,int l,int r){if(l>=r)return;int m=(l+r)>>>1;mergeSort(a,l,m);mergeSort(a,m+1,r);merge(a,l,m,r);}
    private void merge(List<Record>a,int l,int m,int r){List<Record>x=new ArrayList<>(a.subList(l,m+1)),y=new ArrayList<>(a.subList(m+1,r+1));int i=0,j=0,k=l;while(i<x.size()&&j<y.size())a.set(k++,compare(x.get(i),y.get(j))>=0?x.get(i++):y.get(j++));while(i<x.size())a.set(k++,x.get(i++));while(j<y.size())a.set(k++,y.get(j++));}
    private int compare(Record a,Record b){return Double.compare(dec(get(a,percent)),dec(get(b,percent)));}
    private static void accumulate(Map<String,long[]>m,String k,long e,long v){if(k==null||k.isBlank())k="Unknown";long[]t=m.computeIfAbsent(k,x->new long[2]);t[0]+=e;t[1]+=v;}
    private static String top(Map<String,long[]>m){return m.entrySet().stream().max((a,b)->Double.compare(pct(a.getValue()),pct(b.getValue()))).map(Map.Entry::getKey).orElse("N/A");}
    private static double pct(long[]t){return t[1]==0?0:100.0*t[0]/t[1];}
    private static long num(String s){try{return Long.parseLong(s.replace(",","").strip());}catch(Exception e){return 0;}}
    private static long numStrict(String s){try{return Long.parseLong(s.replace(",","").strip());}catch(Exception e){throw new IllegalArgumentException("Expected a whole number: "+s);}}
    private static double dec(String s){try{return Double.parseDouble(s.replace("%","").replace(",","").strip());}catch(Exception e){return 0;}}
    private static double decStrict(String s){try{return Double.parseDouble(s.replace("%","").replace(",","").strip());}catch(Exception e){throw new IllegalArgumentException("Expected a number: "+s);}}
    private static String norm(String s){return s==null?"":s.strip().toLowerCase(Locale.ROOT);}
    private static String get(Record r,int c){return get(r.values,c);} private static String get(String[]r,int c){return c<0||c>=r.length||r[c]==null?"":r[c].strip();}

    /** RFC 4180 parser supporting quoted commas, doubled quotes, and newlines. */
    static List<List<String>> parseCsv(BufferedReader reader)throws IOException{
        List<List<String>>rows=new ArrayList<>();List<String>row=new ArrayList<>();StringBuilder f=new StringBuilder();boolean quoted=false;int x;
        while((x=reader.read())!=-1){char c=(char)x;if(quoted){if(c=='"'){reader.mark(1);int n=reader.read();if(n=='"')f.append('"');else{quoted=false;if(n!=-1)reader.reset();}}else f.append(c);}else if(c=='"'&&f.length()==0)quoted=true;else if(c==','){row.add(f.toString());f.setLength(0);}else if(c=='\n'){row.add(f.toString());rows.add(row);row=new ArrayList<>();f.setLength(0);}else if(c!='\r')f.append(c);}
        if(quoted)throw new IOException("CSV ended inside a quoted field.");if(f.length()>0||!row.isEmpty()){row.add(f.toString());rows.add(row);}return rows;
    }
    static void writeRow(BufferedWriter out,String[]row)throws IOException{for(int i=0;i<row.length;i++){if(i>0)out.write(',');String v=row[i]==null?"":row[i];boolean q=v.matches(".*[,\"\\r\\n].*");if(q)out.write('"');out.write(v.replace("\"","\"\""));if(q)out.write('"');}out.newLine();}
}
