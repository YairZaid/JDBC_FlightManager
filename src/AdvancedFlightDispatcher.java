import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Batch-updates the {@code delay_status} of several arrivals in {@code FlightDB}
 * using a single reusable {@link PreparedStatement}.
 */
public class AdvancedFlightDispatcher {

    /** JDBC connection URL pointing to FlightDB. */
    private static final String URL =
            "jdbc:mysql://localhost:3306/FlightDB?allowLoadLocalInfile=true&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jerusalem&useSSL=false";

    /** Default MySQL administrative user. */
    private static final String USER = "root";

    /** Parameterized update statement shared by every batch entry. */
    private static final String UPDATE_SQL =
            "UPDATE Arrivals SET delay_status = ? WHERE id = ?";

    /**
     * Prompts for the MySQL root password, connects to FlightDB, and marks
     * arrivals 1, 3 and 5 as "Landed - Final".
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Please enter your MySQL root password: ");
            String password = scanner.nextLine();

            try (Connection conn = DriverManager.getConnection(URL, USER, password)) {
                System.out.println("Successfully connected to FlightDB!\n");

                updateMultipleDelays(conn, Arrays.asList(1, 3, 5), "Landed - Final");

            } catch (SQLException e) {
                System.err.println("Database operation failed!");
                System.err.println("Error Code: " + e.getErrorCode());
                System.err.println("Message: " + e.getMessage());
            }
        }
    }

    /**
     * Updates {@code delay_status} for every given arrival ID in a single JDBC batch.
     * The status parameter is bound once; only the ID changes per batch entry.
     *
     * @param conn       active JDBC connection to FlightDB
     * @param arrivalIds IDs of the arrivals to update
     * @param newStatus  new delay status to apply to all of them
     * @throws SQLException if preparing or executing the batch fails
     */
    public static void updateMultipleDelays(Connection conn, List<Integer> arrivalIds, String newStatus)
            throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(UPDATE_SQL)) {
            // Bound once: JDBC keeps parameter values across addBatch() calls
            pstmt.setString(1, newStatus);

            for (Integer id : arrivalIds) {
                pstmt.setInt(2, id);
                pstmt.addBatch();
            }

            int[] results = pstmt.executeBatch();

            int totalAffected = 0;
            boolean unknownCounts = false;
            for (int i = 0; i < results.length; i++) {
                int count = results[i];
                if (count == Statement.SUCCESS_NO_INFO) {
                    unknownCounts = true;
                    System.out.println("  ID " + arrivalIds.get(i) + ": succeeded (row count unavailable)");
                } else {
                    totalAffected += count;
                    System.out.println("  ID " + arrivalIds.get(i) + ": " + count + " row(s) updated");
                }
            }

            System.out.println("\nBatch complete: " + results.length + " statement(s) executed, "
                    + totalAffected + " row(s) set to '" + newStatus + "'"
                    + (unknownCounts ? " (some driver counts unavailable)" : "") + ".");
        }
    }
}
