package com.mysql.monitor;

import com.mysql.cj.jdbc.MysqlDataSource;
import java.sql.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * MySQL连接池
 * 提供高性能的数据库连接管理
 */
public class MySQLConnectionPool {
    private static final Logger logger = Logger.getLogger(MySQLConnectionPool.class.getName());
    
    private final MysqlDataSource dataSource;
    private final BlockingQueue<Connection> connectionPool;
    private final int maxConnections;
    private final String poolName;
    private volatile boolean isClosed = false;

    public MySQLConnectionPool(String url, String username, String password, int maxConnections) {
        this(url, username, password, maxConnections, "default");
    }

    public MySQLConnectionPool(String url, String username, String password, int maxConnections, String poolName) {
        this.maxConnections = maxConnections;
        this.poolName = poolName;
        this.connectionPool = new LinkedBlockingQueue<>(maxConnections);
        
        this.dataSource = new MysqlDataSource();
        this.dataSource.setUrl(url);
        this.dataSource.setUser(username);
        this.dataSource.setPassword(password);
        this.dataSource.setConnectTimeout(5000); // 5秒连接超时
        this.dataSource.setSocketTimeout(10000);   // 10秒socket超时
        
        initializePool();
    }

    /**
     * 初始化连接池
     */
    private void initializePool() {
        try {
            for (int i = 0; i < maxConnections; i++) {
                Connection conn = dataSource.getConnection();
                connectionPool.offer(conn);
            }
            logger.info("Initialized MySQL connection pool: " + poolName + " with " + maxConnections + " connections");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to initialize connection pool", e);
            throw new RuntimeException("Failed to initialize connection pool", e);
        }
    }

    /**
     * 获取连接
     */
    public Connection getConnection() throws SQLException {
        if (isClosed) {
            throw new SQLException("Connection pool is closed");
        }

        try {
            Connection conn = connectionPool.poll(5, TimeUnit.SECONDS);
            if (conn == null) {
                logger.warning("Connection pool exhausted, creating new connection");
                return dataSource.getConnection();
            }
            
            // 验证连接是否有效
            if (!isConnectionValid(conn)) {
                conn.close();
                conn = dataSource.getConnection();
            }
            
            return new PooledConnection(conn, this);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for connection", e);
        }
    }

    /**
     * 验证连接是否有效
     */
    private boolean isConnectionValid(Connection conn) {
        try {
            if (conn == null || conn.isClosed()) {
                return false;
            }
            
            // 执行简单查询验证连接
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
                return true;
            }
        } catch (SQLException e) {
            logger.log(Level.FINE, "Connection validation failed", e);
            return false;
        }
    }

    /**
     * 归还连接到池
     */
    void returnConnection(Connection conn) {
        if (isClosed || conn == null) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing connection", e);
            }
            return;
        }

        try {
            if (!conn.isClosed() && !connectionPool.offer(conn)) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error returning connection to pool", e);
        }
    }

    /**
     * 关闭连接池
     */
    public void close() {
        isClosed = true;
        
        for (Connection conn : connectionPool) {
            try {
                if (conn != null && !conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing connection", e);
            }
        }
        
        connectionPool.clear();
        logger.info("Closed MySQL connection pool: " + poolName);
    }

    /**
     * 获取连接池状态
     */
    public PoolStatus getStatus() {
        PoolStatus status = new PoolStatus();
        status.setPoolName(poolName);
        status.setActiveConnections(maxConnections - connectionPool.size());
        status.setIdleConnections(connectionPool.size());
        status.setMaxConnections(maxConnections);
        status.setClosed(isClosed);
        return status;
    }

    /**
     * 池化连接包装器
     */
    private static class PooledConnection implements Connection {
        private final Connection delegate;
        private final MySQLConnectionPool pool;
        private volatile boolean isClosed = false;

        PooledConnection(Connection delegate, MySQLConnectionPool pool) {
            this.delegate = delegate;
            this.pool = pool;
        }

        @Override
        public void close() throws SQLException {
            if (!isClosed) {
                isClosed = true;
                pool.returnConnection(delegate);
            }
        }

        @Override
        public boolean isClosed() throws SQLException {
            return isClosed || delegate.isClosed();
        }

        // 委托所有其他方法
        @Override
        public Statement createStatement() throws SQLException {
            checkClosed();
            return delegate.createStatement();
        }

        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            checkClosed();
            return delegate.prepareStatement(sql);
        }

        @Override
        public CallableStatement prepareCall(String sql) throws SQLException {
            checkClosed();
            return delegate.prepareCall(sql);
        }

        @Override
        public String nativeSQL(String sql) throws SQLException {
            checkClosed();
            return delegate.nativeSQL(sql);
        }

        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {
            checkClosed();
            delegate.setAutoCommit(autoCommit);
        }

        @Override
        public boolean getAutoCommit() throws SQLException {
            checkClosed();
            return delegate.getAutoCommit();
        }

        @Override
        public void commit() throws SQLException {
            checkClosed();
            delegate.commit();
        }

        @Override
        public void rollback() throws SQLException {
            checkClosed();
            delegate.rollback();
        }

        @Override
        public DatabaseMetaData getMetaData() throws SQLException {
            checkClosed();
            return delegate.getMetaData();
        }

        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {
            checkClosed();
            delegate.setReadOnly(readOnly);
        }

        @Override
        public boolean isReadOnly() throws SQLException {
            checkClosed();
            return delegate.isReadOnly();
        }

        @Override
        public void setCatalog(String catalog) throws SQLException {
            checkClosed();
            delegate.setCatalog(catalog);
        }

        @Override
        public String getCatalog() throws SQLException {
            checkClosed();
            return delegate.getCatalog();
        }

        @Override
        public void setTransactionIsolation(int level) throws SQLException {
            checkClosed();
            delegate.setTransactionIsolation(level);
        }

        @Override
        public int getTransactionIsolation() throws SQLException {
            checkClosed();
            return delegate.getTransactionIsolation();
        }

        @Override
        public SQLWarning getWarnings() throws SQLException {
            checkClosed();
            return delegate.getWarnings();
        }

        @Override
        public void clearWarnings() throws SQLException {
            checkClosed();
            delegate.clearWarnings();
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
            checkClosed();
            return delegate.createStatement(resultSetType, resultSetConcurrency);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            checkClosed();
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
            checkClosed();
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
        }

        @Override
        public Map<String, Class<?>> getTypeMap() throws SQLException {
            checkClosed();
            return delegate.getTypeMap();
        }

        @Override
        public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
            checkClosed();
            delegate.setTypeMap(map);
        }

        @Override
        public void setHoldability(int holdability) throws SQLException {
            checkClosed();
            delegate.setHoldability(holdability);
        }

        @Override
        public int getHoldability() throws SQLException {
            checkClosed();
            return delegate.getHoldability();
        }

        @Override
        public Savepoint setSavepoint() throws SQLException {
            checkClosed();
            return delegate.setSavepoint();
        }

        @Override
        public Savepoint setSavepoint(String name) throws SQLException {
            checkClosed();
            return delegate.setSavepoint(name);
        }

        @Override
        public void rollback(Savepoint savepoint) throws SQLException {
            checkClosed();
            delegate.rollback(savepoint);
        }

        @Override
        public void releaseSavepoint(Savepoint savepoint) throws SQLException {
            checkClosed();
            delegate.releaseSavepoint(savepoint);
        }

        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkClosed();
            return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkClosed();
            return delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
            checkClosed();
            return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
            checkClosed();
            return delegate.prepareStatement(sql, autoGeneratedKeys);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
            checkClosed();
            return delegate.prepareStatement(sql, columnIndexes);
        }

        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
            checkClosed();
            return delegate.prepareStatement(sql, columnNames);
        }

        @Override
        public Clob createClob() throws SQLException {
            checkClosed();
            return delegate.createClob();
        }

        @Override
        public Blob createBlob() throws SQLException {
            checkClosed();
            return delegate.createBlob();
        }

        @Override
        public NClob createNClob() throws SQLException {
            checkClosed();
            return delegate.createNClob();
        }

        @Override
        public SQLXML createSQLXML() throws SQLException {
            checkClosed();
            return delegate.createSQLXML();
        }

        @Override
        public boolean isValid(int timeout) throws SQLException {
            checkClosed();
            return delegate.isValid(timeout);
        }

        @Override
        public void setClientInfo(String name, String value) throws SQLClientInfoException {
            delegate.setClientInfo(name, value);
        }

        @Override
        public void setClientInfo(Properties properties) throws SQLClientInfoException {
            delegate.setClientInfo(properties);
        }

        @Override
        public String getClientInfo(String name) throws SQLException {
            checkClosed();
            return delegate.getClientInfo(name);
        }

        @Override
        public Properties getClientInfo() throws SQLException {
            checkClosed();
            return delegate.getClientInfo();
        }

        @Override
        public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            checkClosed();
            return delegate.createArrayOf(typeName, elements);
        }

        @Override
        public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            checkClosed();
            return delegate.createStruct(typeName, attributes);
        }

        @Override
        public void setSchema(String schema) throws SQLException {
            checkClosed();
            delegate.setSchema(schema);
        }

        @Override
        public String getSchema() throws SQLException {
            checkClosed();
            return delegate.getSchema();
        }

        @Override
        public void abort(Executor executor) throws SQLException {
            checkClosed();
            delegate.abort(executor);
        }

        @Override
        public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
            checkClosed();
            delegate.setNetworkTimeout(executor, milliseconds);
        }

        @Override
        public int getNetworkTimeout() throws SQLException {
            checkClosed();
            return delegate.getNetworkTimeout();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            checkClosed();
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            checkClosed();
            return delegate.isWrapperFor(iface);
        }

        private void checkClosed() throws SQLException {
            if (isClosed) {
                throw new SQLException("Connection is closed");
            }
        }
    }

    /**
     * 连接池状态
     */
    public static class PoolStatus {
        private String poolName;
        private int activeConnections;
        private int idleConnections;
        private int maxConnections;
        private boolean closed;

        // Getters and setters
        public String getPoolName() { return poolName; }
        public void setPoolName(String poolName) { this.poolName = poolName; }
        
        public int getActiveConnections() { return activeConnections; }
        public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }
        
        public int getIdleConnections() { return idleConnections; }
        public void setIdleConnections(int idleConnections) { this.idleConnections = idleConnections; }
        
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        
        public boolean isClosed() { return closed; }
        public void setClosed(boolean closed) { this.closed = closed; }

        @Override
        public String toString() {
            return "PoolStatus{" +
                    "poolName='" + poolName + '\'' +
                    ", activeConnections=" + activeConnections +
                    ", idleConnections=" + idleConnections +
                    ", maxConnections=" + maxConnections +
                    ", closed=" + closed +
                    '}';
        }
    }
}