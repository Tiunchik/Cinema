/**
 * Package servlets.user for
 *
 * @author Maksim Tiunchik
 */
package org.cinema;

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Class DBStore - class for work with PSQL DB during multithreading sessions
 *
 * @author Maksim Tiunchik (senebh@gmail.com)
 * @version 0.1
 * @since 21.03.2020
 */
@ThreadSafe
public class DBStore implements Base {

    /**
     * inner logger
     */
    private static final Logger LOG = LogManager.getLogger(DBStore.class.getName());

    /**
     * special version of JDBC for multithreading sessions
     */
    private static final BasicDataSource SOURCE = new BasicDataSource();

    /**
     * singleton for DB
     */
    private static final DBStore BASE = new DBStore();

    /**
     * private constructor to set all properties for connection
     */
    private DBStore() {
        SOURCE.setDriverClassName("org.postgresql.Driver");
        SOURCE.setUrl("jdbc:postgresql://127.0.0.1:5432/cinema");
        SOURCE.setUsername("postgres");
        SOURCE.setPassword("password");
        SOURCE.setMinIdle(5);
        SOURCE.setMaxIdle(10);
        SOURCE.setMaxOpenPreparedStatements(100);
        createTB();
        createHall1();
    }

    /**
     * get SOURCE connection
     *
     * @return connection
     * @throws SQLException
     */
    private Connection getConnection() throws SQLException {
        return SOURCE.getConnection();
    }

    /**
     * static method to get link to DBStore
     *
     * @return link to DBStore
     */
    public static DBStore getInstance() {
        return BASE;
    }

    /**
     * create table
     */
    private void createTB() {
        try (Connection connection = SOURCE.getConnection();
             Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS ACCOUNT ("
                    + "    ID   SERIAL PRIMARY KEY, "
                    + "    NAME VARCHAR(100))");
            st.execute("CREATE TABLE IF NOT EXISTS HALL1 ("
                    + " ROW   INTEGER,"
                    + " PLACE INTEGER,"
                    + " ACC   INTEGER REFERENCES ACCOUNT (ID) ON DELETE CASCADE,"
                    + " CONSTRAINT unique_place UNIQUE (ROW, PLACE))");
            st.execute("INSERT INTO ACCOUNT (ID, NAME)"
                    + " VALUES (0, 'CINEMA')"
                    + " ON CONFLICT (ID) DO UPDATE SET NAME ='CINEMA'");
        } catch (SQLException e) {
            LOG.error("create TB method SQL ecxeption", e);
        }
    }

    /**
     * create one hall into DB
     */
    private void createHall1() {
        try (Connection connection = SOURCE.getConnection()) {
            PreparedStatement st = connection.prepareStatement("INSERT INTO HALL1 (ROW, PLACE, ACC) "
                    + "VALUES (?, ?, ?) ON CONFLICT (ROW, PLACE) DO NOTHING ");
            for (int r = 1; r <= 10; r++) {
                for (int p = 1; p <= 10; p++) {
                    st.setInt(1, r);
                    st.setInt(2, p);
                    st.setInt(3, 0);
                    st.addBatch();
                }
            }
            st.executeBatch();
        } catch (SQLException e) {
            LOG.error("create HALL1 method SQL ecxeption", e);
        }
    }

    /**
     * return collection to fullfilling page of cinemahall
     *
     * @return list of list of places
     */
    public List<Place> getHall1() {
        List<Place> hall = new ArrayList<>(100);
        try (Connection connection = SOURCE.getConnection()) {
            PreparedStatement st = connection.prepareStatement("SELECT ROW, PLACE, ACCOUNT.ID FROM HALL1 "
                    + "LEFT OUTER JOIN ACCOUNT ON HALL1.ACC = ACCOUNT.ID;");
            ResultSet total = st.executeQuery();
            while (total.next()) {
                hall.add(new Place(total.getInt("ROW"),
                        total.getInt("PLACE"), total.getInt("ID")));
            }
        } catch (SQLException e) {
            LOG.error("get places method SQL ecxeption", e);
        }
        return hall;
    }

    /**
     * purchasing one place
     *
     * @param place information about parchasing plase
     */
    public void purchaise(Place place) {
        int id = Objects.hash(place.getName());
        Connection connection = null;
        try {
            connection = SOURCE.getConnection();
            connection.setAutoCommit(false);
            PreparedStatement st = connection.prepareStatement("INSERT INTO ACCOUNT (ID, NAME) VALUES (?,?) ON CONFLICT (ID) DO NOTHING ");
            st.setInt(1, id);
            st.setString(2, place.getName());
            st.execute();
            st = connection.prepareStatement("UPDATE HALL1 SET ACC=? WHERE ROW=? and PLACE=?");
            st.setInt(1, id);
            st.setInt(2, place.getRow());
            st.setInt(3, place.getPlace());
            st.execute();
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            try {
                if (connection != null) {
                    connection.rollback();
                }
            } catch (SQLException ex) {
                ex.setNextException(e);
                LOG.error("rollback exception", ex);
            }
            LOG.error("set places method SQL ecxeption", e);
        } finally {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                LOG.error("close connection exception", e);
            }
        }
    }
}
