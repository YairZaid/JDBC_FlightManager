import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Scanner;

/**
 * Updates the {@code delay_status} of flight {@value #FLIGHT_NUMBER} in {@code FlightDB}.
 *
 * <h2>Why the previous version was vulnerable to SQL injection</h2>
 * <p>
 * The original implementation built the SQL text by string concatenation and ran it
 * through a plain {@link java.sql.Statement}:
 * </p>
 * <pre>{@code
 * String sql = "UPDATE Arrivals SET delay_status = '" + newStatus +
 *              "' WHERE flight_number = '" + FLIGHT_NUMBER + "'";
 * stmt.executeUpdate(sql);
 * }</pre>
 * <p>
 * Because {@code newStatus} comes straight from the keyboard, the user controls part of
 * the <em>SQL source code</em>, not just a value. A single quote in the input closes the
 * string literal early, and everything after it is parsed as SQL. For example, entering
 * </p>
 * <pre>{@code Delayed' -- }</pre>
 * <p>
 * produces
 * </p>
 * <pre>{@code UPDATE Arrivals SET delay_status = 'Delayed' -- ' WHERE flight_number = 'LX354'}</pre>
 * <p>
 * The {@code --} turns the {@code WHERE} clause into a comment, so <strong>every row</strong>
 * in {@code Arrivals} is overwritten instead of just flight LX354. Other payloads can inject
 * extra assignments ({@code x', terminal = '9}) or subqueries that read other tables. The
 * risk is higher here because the program connects as {@code root}, which has full privileges.
 * Input that merely contains an apostrophe (e.g. {@code Pilot's delay}) also breaks the
 * query with a syntax error, so the concatenated version was fragile as well as unsafe.
 * </p>
 *
 * <h2>How {@link PreparedStatement} prevents it</h2>
 * <ul>
 *   <li><strong>Code and data are separated.</strong> The SQL text with {@code ?} placeholders
 *       is fixed at compile time and sent to the driver/server by
 *       {@link Connection#prepareStatement(String)} <em>before</em> any user input is
 *       involved. The structure of the query (which table, which columns, which
 *       {@code WHERE} condition) cannot change afterwards.</li>
 *   <li><strong>Parameters are bound as values, never parsed as SQL.</strong>
 *       {@link PreparedStatement#setString(int, String)} transmits the input as a typed
 *       string value. Quotes, {@code --}, semicolons and keywords inside it are treated as
 *       literal characters, so {@code Delayed' --} is stored verbatim as the status text
 *       and only LX354 is affected.</li>
 *   <li><strong>Type safety.</strong> Each placeholder is bound with a typed setter
 *       ({@code setString}, {@code setInt}, ...), so the driver handles conversion and
 *       escaping correctly instead of relying on hand-written quoting.</li>
 * </ul>
 * <p>
 * Resources ({@link Scanner}, {@link Connection}, {@link PreparedStatement}) are opened in
 * try-with-resources blocks so they are closed automatically, even when an exception is thrown.
 * </p>
 */
public class SimpleFlightUpdater {

    /** JDBC connection URL pointing to FlightDB. */
    private static final String URL =
            "jdbc:mysql://localhost:3306/FlightDB?allowLoadLocalInfile=true&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jerusalem&useSSL=false";

    /** Default MySQL administrative user. */
    private static final String USER = "root";

    /** Flight whose delay status is updated. */
    private static final String FLIGHT_NUMBER = "LX354";

    /**
     * Parameterized update statement. The two {@code ?} placeholders are filled by
     * {@link PreparedStatement#setString(int, String)} and are never concatenated into the SQL text.
     */
    private static final String UPDATE_SQL =
            "UPDATE Arrivals SET delay_status = ? WHERE flight_number = ?";

    /**
     * Prompts for the MySQL root password and a new delay status for flight
     * {@value #FLIGHT_NUMBER}, executes the parameterized update, and prints the
     * number of affected rows.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Please enter your MySQL root password: ");
            String password = scanner.nextLine();

            System.out.print("Enter new delay status for flight " + FLIGHT_NUMBER + ": ");
            String newStatus = scanner.nextLine();

            try (Connection conn = DriverManager.getConnection(URL, USER, password);
                 PreparedStatement pstmt = conn.prepareStatement(UPDATE_SQL)) {

                // Bind user input as data: it can no longer alter the query's structure
                pstmt.setString(1, newStatus);
                pstmt.setString(2, FLIGHT_NUMBER);

                int affectedRows = pstmt.executeUpdate();
                System.out.println("Rows affected: " + affectedRows);

            } catch (SQLException e) {
                System.err.println("Database operation failed!");
                System.err.println("Error Code: " + e.getErrorCode());
                System.err.println("Message: " + e.getMessage());
            }
        }
    }
}
