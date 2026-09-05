import java.io.*;
import java.util.*;

public class CSVDatabase {
    // Column indexes for the CS 508 data set (0-based)
    private static final int COL_STATE = 2;
    private static final int COL_BEV = 4;
    private static final int COL_PHEV = 5;
    private static final int COL_EV_PERCENT = 9;

    // Cap on how many rows we dump to the console at once
    private static final int MAX_LIST = 25;

    private static String filePath = "/Users/daddy/Downloads/CS 508 Project Two Data Set.csv";

    // All records, kept sorted by EV percentage descending
    private static final List<String[]> data = new ArrayList<>();
    // State code -> that state's records, each list also kept in descending EV % order
    private static final Map<String, List<String[]>> stateMap = new HashMap<>();
    private static String[] header = null;
    private static boolean unsavedChanges = false;

    public static void main(String[] args) {
        if (args.length > 0) {
            filePath = args[0];
        }

        if (!loadData()) {
            return;
        }
        if (data.isEmpty()) {
            System.out.println("No data found.");
            return;
        }

        // Merge sort by EV percentage descending (modifies data in place)
        mergeSort(data, 0, data.size() - 1);

        // HashMap for optimal state lookup
        buildIndex();

        System.out.println("Loaded " + data.size() + " records from " + filePath);
        generateStatistics();

        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            printMenu();
            if (!scanner.hasNextLine()) break;
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    searchByState(scanner);
                    break;
                case "2":
                    addRecord(scanner);
                    break;
                case "3":
                    deleteRecord(scanner);
                    break;
                case "4":
                    generateStatistics();
                    break;
                case "5":
                    saveToFile();
                    break;
                case "6":
                case "":
                    if (unsavedChanges) {
                        System.out.print("You have unsaved changes. Save before exit? (y/n): ");
                        if (scanner.hasNextLine() && scanner.nextLine().trim().equalsIgnoreCase("y")) {
                            saveToFile();
                        }
                    }
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Enter a number from 1 to 6.");
            }
        }

        System.out.println("Goodbye.");
        scanner.close();
    }

    private static void printMenu() {
        System.out.println("\n================ MENU ================");
        System.out.println("1. Search records by state");
        System.out.println("2. Add a record");
        System.out.println("3. Delete a record");
        System.out.println("4. Show summary statistics");
        System.out.println("5. Save to file");
        System.out.println("6. Exit");
        System.out.println("======================================");
        System.out.print("Choice: ");
    }

    // ---------- Load / save ----------

    private static boolean loadData() {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            if (line != null) {
                header = line.split(",", -1);
            }
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                data.add(line.split(",", -1));
            }
            return true;
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return false;
        }
    }

    private static void saveToFile() {
        String outPath = filePath.toLowerCase().endsWith(".csv")
                ? filePath.substring(0, filePath.length() - 4) + ".modified.csv"
                : filePath + ".modified.csv";

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(outPath)))) {
            if (header != null) {
                out.println(String.join(",", header));
            }
            for (String[] row : data) {
                out.println(String.join(",", row));
            }
        } catch (IOException e) {
            System.err.println("Error writing file: " + e.getMessage());
            return;
        }

        unsavedChanges = false;
        System.out.println("Saved " + data.size() + " records to:");
        System.out.println("  " + outPath);
        System.out.println("(original file unchanged)");
    }

    // ---------- Index maintenance ----------

    /** Rebuilds the state index from data. data must already be sorted. */
    private static void buildIndex() {
        stateMap.clear();
        for (String[] row : data) {
            if (row.length > COL_STATE) {
                stateMap.computeIfAbsent(stateKey(row), k -> new ArrayList<>()).add(row);
            }
        }
    }

    private static String stateKey(String[] row) {
        return row[COL_STATE].trim().toUpperCase();
    }

    /**
     * Inserts row at its correct position in a list already sorted by EV % descending.
     * Binary search for the position, so no re-sort is needed after an add.
     */
    private static void insertSorted(List<String[]> list, String[] row) {
        int lo = 0, hi = list.size();
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (compare(list.get(mid), row) >= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        list.add(lo, row);
    }

    /** Removes a record from both data and the state index. */
    private static void removeRecord(String[] row) {
        removeByIdentity(data, row);
        if (row.length > COL_STATE) {
            String key = stateKey(row);
            List<String[]> list = stateMap.get(key);
            if (list != null) {
                removeByIdentity(list, row);
                if (list.isEmpty()) {
                    stateMap.remove(key);
                }
            }
        }
    }

    /**
     * Removes the exact array instance, not an equal-looking one. Duplicate rows are
     * common in this data set, so identity is what distinguishes the one the user picked.
     */
    private static boolean removeByIdentity(List<String[]> list, String[] row) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == row) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    // ---------- Search ----------

    private static void searchByState(Scanner scanner) {
        System.out.print("\nState code to search (e.g. WA), or blank to cancel: ");
        if (!scanner.hasNextLine()) return;
        String state = scanner.nextLine().trim().toUpperCase();
        if (state.isEmpty()) return;

        List<String[]> results = stateMap.get(state);
        if (results == null || results.isEmpty()) {
            System.out.println("No records found for state: " + state);
            return;
        }

        System.out.println("\nRecords for " + state + " (sorted by EV % descending):");
        printRows(results, false);
        System.out.println("Total records for " + state + ": " + results.size());
    }

    /** Prints up to MAX_LIST rows, optionally numbered, under the header row. */
    private static void printRows(List<String[]> rows, boolean numbered) {
        String pad = numbered ? "      " : "";
        if (header != null) {
            System.out.println(pad + String.join(" | ", header));
            System.out.println("-".repeat(80));
        }
        int shown = Math.min(rows.size(), MAX_LIST);
        for (int i = 0; i < shown; i++) {
            String joined = String.join(" | ", rows.get(i));
            if (numbered) {
                System.out.printf("%5d. %s%n", i + 1, joined);
            } else {
                System.out.println(joined);
            }
        }
        if (rows.size() > shown) {
            System.out.println("... " + (rows.size() - shown) + " more not shown (highest EV % listed first)");
        }
    }

    // ---------- Add ----------

    private static void addRecord(Scanner scanner) {
        if (header == null) {
            System.out.println("No header row available, cannot add records.");
            return;
        }

        System.out.println("\n--- Add a record (blank entry at any prompt cancels) ---");
        String[] row = new String[header.length];

        for (int i = 0; i < header.length; i++) {
            while (true) {
                System.out.print("  " + header[i] + ": ");
                if (!scanner.hasNextLine()) return;
                String value = scanner.nextLine().trim();

                if (value.isEmpty()) {
                    System.out.println("Add cancelled.");
                    return;
                }
                if (value.contains(",")) {
                    System.out.println("    Value cannot contain a comma, it would break the CSV format.");
                    continue;
                }
                if ((i == COL_BEV || i == COL_PHEV) && !isLong(value)) {
                    System.out.println("    Expected a whole number.");
                    continue;
                }
                if (i == COL_EV_PERCENT && !isDouble(value)) {
                    System.out.println("    Expected a number, e.g. 7.80");
                    continue;
                }
                if (i == COL_STATE) {
                    value = value.toUpperCase();
                }

                row[i] = value;
                break;
            }
        }

        insertSorted(data, row);
        insertSorted(stateMap.computeIfAbsent(stateKey(row), k -> new ArrayList<>()), row);
        unsavedChanges = true;

        System.out.println("Record added. Database now holds " + data.size() + " records.");
    }

    // ---------- Delete ----------

    private static void deleteRecord(Scanner scanner) {
        System.out.print("\nState code of the record to delete (e.g. WA), or blank to cancel: ");
        if (!scanner.hasNextLine()) return;
        String state = scanner.nextLine().trim().toUpperCase();
        if (state.isEmpty()) return;

        List<String[]> indexed = stateMap.get(state);
        if (indexed == null || indexed.isEmpty()) {
            System.out.println("No records found for state: " + state);
            return;
        }

        // Work on a copy so narrowing and display never disturb the index
        List<String[]> matches = new ArrayList<>(indexed);

        if (matches.size() > MAX_LIST) {
            System.out.println(matches.size() + " records for " + state + " is too many to list.");
            System.out.print("Text to narrow by (county, date, ...), 'all' to delete every "
                    + state + " record, or blank to cancel: ");
            if (!scanner.hasNextLine()) return;
            String filter = scanner.nextLine().trim();

            if (filter.isEmpty()) {
                System.out.println("Delete cancelled.");
                return;
            }
            if (filter.equalsIgnoreCase("all")) {
                deleteAll(scanner, state, matches);
                return;
            }

            matches = filterRows(matches, filter);
            if (matches.isEmpty()) {
                System.out.println("No " + state + " records contain \"" + filter + "\".");
                return;
            }
        }

        System.out.println("\nMatching records for " + state + " (sorted by EV % descending):");
        printRows(matches, true);

        int listed = Math.min(matches.size(), MAX_LIST);
        System.out.print("Number to delete (1-" + listed + "), 'all' to delete all "
                + matches.size() + " matches, or blank to cancel: ");
        if (!scanner.hasNextLine()) return;
        String choice = scanner.nextLine().trim();

        if (choice.isEmpty()) {
            System.out.println("Delete cancelled.");
            return;
        }
        if (choice.equalsIgnoreCase("all")) {
            deleteAll(scanner, state, matches);
            return;
        }

        int index;
        try {
            index = Integer.parseInt(choice);
        } catch (NumberFormatException e) {
            System.out.println("Not a valid number. Delete cancelled.");
            return;
        }
        if (index < 1 || index > listed) {
            System.out.println("Number out of range. Delete cancelled.");
            return;
        }

        removeRecord(matches.get(index - 1));
        unsavedChanges = true;
        System.out.println("Deleted 1 record. Database now holds " + data.size() + " records.");
    }

    private static void deleteAll(Scanner scanner, String state, List<String[]> rows) {
        System.out.print("Delete all " + rows.size() + " matching " + state
                + " record(s)? This cannot be undone. (y/n): ");
        if (!scanner.hasNextLine()) return;
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Delete cancelled.");
            return;
        }

        for (String[] row : rows) {
            removeRecord(row);
        }
        unsavedChanges = true;
        System.out.println("Deleted " + rows.size() + " record(s). Database now holds "
                + data.size() + " records.");
    }

    /** Keeps rows where any field contains the given text, case-insensitively. */
    private static List<String[]> filterRows(List<String[]> rows, String text) {
        String needle = text.toLowerCase();
        List<String[]> out = new ArrayList<>();
        for (String[] row : rows) {
            for (String field : row) {
                if (field.toLowerCase().contains(needle)) {
                    out.add(row);
                    break;
                }
            }
        }
        return out;
    }

    // ---------- Statistics ----------

    private static void generateStatistics() {
        double sumPercent = 0;
        long totalEVs = 0;
        double maxPercent = Double.NEGATIVE_INFINITY;
        String maxState = "N/A";
        int validCount = 0;

        for (String[] row : data) {
            try {
                if (row.length > COL_EV_PERCENT) {
                    double pct = Double.parseDouble(row[COL_EV_PERCENT].replace("%", "").trim());
                    sumPercent += pct;
                    validCount++;
                    if (pct > maxPercent) {
                        maxPercent = pct;
                        maxState = row.length > COL_STATE ? row[COL_STATE].trim() : "N/A";
                    }
                }
                long bev = 0, phev = 0;
                if (row.length > COL_BEV) {
                    bev = Long.parseLong(row[COL_BEV].replace(",", "").trim());
                }
                if (row.length > COL_PHEV) {
                    phev = Long.parseLong(row[COL_PHEV].replace(",", "").trim());
                }
                totalEVs += bev + phev;
            } catch (NumberFormatException ignored) {
            }
        }

        double avgPercent = validCount > 0 ? sumPercent / validCount : 0;
        if (validCount == 0) {
            maxPercent = 0;
        }

        System.out.println("\n========== SUMMARY STATISTICS ==========");
        System.out.println("Records in database: " + data.size());
        System.out.printf("A. Average percentage of EVs across all records: %.2f%%%n", avgPercent);
        System.out.println("B. Total number of EVs (BEVs + PHEVs) across all records: " + totalEVs);
        System.out.printf("C. State with the highest percentage of EVs: %s (%.2f%%)%n", maxState, maxPercent);
        System.out.println("========================================");
    }

    // ---------- Sorting ----------

    private static void mergeSort(List<String[]> arr, int left, int right) {
        if (left < right) {
            int mid = left + (right - left) / 2;
            mergeSort(arr, left, mid);
            mergeSort(arr, mid + 1, right);
            merge(arr, left, mid, right);
        }
    }

    private static void merge(List<String[]> arr, int left, int mid, int right) {
        int n1 = mid - left + 1;
        int n2 = right - mid;

        String[][] L = new String[n1][];
        String[][] R = new String[n2][];

        for (int i = 0; i < n1; i++) L[i] = arr.get(left + i);
        for (int j = 0; j < n2; j++) R[j] = arr.get(mid + 1 + j);

        int i = 0, j = 0, k = left;
        while (i < n1 && j < n2) {
            if (compare(L[i], R[j]) >= 0) {  // descending
                arr.set(k++, L[i++]);
            } else {
                arr.set(k++, R[j++]);
            }
        }
        while (i < n1) arr.set(k++, L[i++]);
        while (j < n2) arr.set(k++, R[j++]);
    }

    private static int compare(String[] a, String[] b) {
        double pa = 0, pb = 0;
        try {
            if (a.length > COL_EV_PERCENT)
                pa = Double.parseDouble(a[COL_EV_PERCENT].replace("%", "").trim());
            if (b.length > COL_EV_PERCENT)
                pb = Double.parseDouble(b[COL_EV_PERCENT].replace("%", "").trim());
        } catch (NumberFormatException ignored) {
        }
        return Double.compare(pa, pb);
    }

    // ---------- Small parsing helpers ----------

    private static boolean isLong(String value) {
        try {
            Long.parseLong(value.replace(",", "").trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDouble(String value) {
        try {
            Double.parseDouble(value.replace("%", "").trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
