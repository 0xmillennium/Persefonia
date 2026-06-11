package dev.persefonia.app.contentpublishing.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class PublishingSql {
    private PublishingSql() {
    }

    static void execute(String sql, Object... args) throws SQLException {
        update(sql, args);
    }

    static int update(String sql, Object... args) throws SQLException {
        try (var connection = PublishingMigrationDatabase.POSTGRES.createConnection("");
                var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            return statement.executeUpdate();
        }
    }

    static List<String> strings(String sql, Object... args) throws SQLException {
        try (var connection = PublishingMigrationDatabase.POSTGRES.createConnection("");
                var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                var values = new ArrayList<String>();
                while (result.next()) {
                    values.add(result.getString(1));
                }
                return values;
            }
        }
    }

    static long count(String sql, Object... args) throws SQLException {
        try (var connection = PublishingMigrationDatabase.POSTGRES.createConnection("");
                var statement = connection.prepareStatement(sql)) {
            bind(statement, args);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... args) throws SQLException {
        for (int index = 0; index < args.length; index++) {
            statement.setObject(index + 1, args[index]);
        }
    }
}
