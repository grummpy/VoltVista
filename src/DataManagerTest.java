import java.nio.file.Path;
import java.util.List;

public final class DataManagerTest {
    public static void main(String[] args) throws Exception {
        DataManager dm = new DataManager(); dm.load(Path.of(args[0]));
        check(dm.size() > 24_000, "full dataset loaded");
        check(dm.header().length == 10, "10 columns detected");
        check(dm.states().contains("wa"), "state HashMap index built");
        List<DataManager.Record> wa = dm.query("WA", "", "Passenger", "");
        check(!wa.isEmpty(), "state and primary-use filtering works");
        double previous = Double.POSITIVE_INFINITY;
        for (DataManager.Record r : dm.query("", "", "", "")) {
            double current = Double.parseDouble(r.values[9]);
            check(current <= previous, "merge sort descending"); previous = current;
        }
        int before = dm.size();
        String[] row = {"September 5 2026","Test County","ZZ","Passenger","2","1","3","97","100","3.00"};
        DataManager.Record added = dm.add(row);
        check(dm.size() == before + 1, "add works");
        check(dm.delete(added.id) && dm.size() == before, "delete works");
        DataManager.Stats stats = dm.stats(wa);
        check(stats.evTotal() >= 0 && stats.weightedEvPercent() >= 0, "analytics work");
        System.out.println("All VoltVista backend tests passed. Rows: " + dm.size());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
