package endgame.plugin.database;

/**
 * Wraps SQL and connection errors with context for the EndgameQoL database layer.
 */
public class DatabaseSyncException extends RuntimeException {

    public DatabaseSyncException(String message) {
        super(message);
    }

    public DatabaseSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
