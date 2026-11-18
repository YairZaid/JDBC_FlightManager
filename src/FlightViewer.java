import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * Displays the arrivals for a single terminal in {@code FlightDB}, joined with
 * {@code FlightRoutes} and sorted by scheduled time.
 * <p>
 * The terminal is bound through a {@link PreparedStatement} parameter so user input
 * is never interpreted as SQL.
 * </p>
 */
public class FlightViewer {

    /** JDBC connection URL pointing to FlightDB. */
    private static final String URL =
            "jdbc:mysql://localhost:3306/FlightDB?allowLoadLocalInfile=true&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jerusalem&useSSL=false";

    /** Default MySQL administrative user. */
    private static final String USER = "root";

    /** Parameterized join query filtered by terminal and ordered by scheduled time. */
    private static final String SELECT_SQL =
            "SELECT Arrivals.id, Arrivals.flight_number, FlightRoutes.from_city, " +
            "Arrivals.scheduled, Arrivals.delay_status, Arrivals.terminal " +
            "FROM Arrivals " +
            "INNER JOIN FlightRoutes ON Arrivals.flight_number = FlightRoutes.flight_number " +
            "WHERE Arrivals.terminal = ? " +
            "ORDER BY Arrivals.scheduled ASC";

    /**
     * Prompts for the MySQL root password and a terminal number, then prints all
     * arrivals at that terminal.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Please enter your MySQL root password: ");
            String password = scanner.nextLine();

            System.out.print("Enter target terminal number (2, 3, 4, 5): ");
            String terminal = scanner.nextLine().trim();

            try (Connection conn = DriverManager.getConnection(URL, USER, password)) {
                System.out.println("Successfully connected to FlightDB!\n");
                displayTerminalArrivals(conn, terminal);

            } catch (SQLException e) {
                System.err.println("Database operation failed!");
                System.err.println("Error Code: " + e.getErrorCode());
                System.err.println("Message: " + e.getMessage());
            }
        }
    }

    /**
     * Queries arrivals at the given terminal and prints them as a formatted table.
     *
     * @param conn     active JDBC connection to FlightDB
     * @param terminal terminal to filter by ({@code VARCHAR(10)} column, so bound as a string)
     * @throws SQLException if preparing or executing the query fails
     */
    public static void displayTerminalArrivals(Connection conn, String terminal) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(SELECT_SQL)) {
            pstmt.setString(1, terminal);

            try (ResultSet rs = pstmt.executeQuery()) {
                System.out.println(" --- Arrivals at Terminal " + terminal + " ---");
                System.out.printf("%-5s | %-10s | %-15s | %-10s | %-20s | %-10s%n",
                        "ID", "Flight", "From", "Scheduled", "Status", "Terminal");
                System.out.println("--------------------------------------------------------------------------------------");

                int rowCount = 0;
                while (rs.next()) {
                    System.out.printf("%-5d | %-10s | %-15s | %-10s | %-20s | %-10s%n",
                            rs.getInt("id"),
                            rs.getString("flight_number"),
                            rs.getString("from_city"),
                            rs.getString("scheduled"),
                            rs.getString("delay_status"),
                            rs.getString("terminal"));
                    rowCount++;
                }
                System.out.println("--------------------------------------------------------------------------------------");

                if (rowCount == 0) {
                    System.out.println("No arrivals found for terminal " + terminal + ".");
                } else {
                    System.out.println("Total flights displayed: " + rowCount);
                }
            }
        }
    }
}
