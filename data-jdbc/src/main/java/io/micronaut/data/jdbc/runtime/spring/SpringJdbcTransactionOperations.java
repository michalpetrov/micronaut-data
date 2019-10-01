package io.micronaut.data.jdbc.runtime.spring;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.TransactionStatus;
import io.micronaut.transaction.exceptions.NoTransactionException;
import io.micronaut.transaction.exceptions.TransactionException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * Default implementation of {@link TransactionOperations} that uses Spring managed transactions.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachBean(DataSourceTransactionManager.class)
@Internal
@Requires(classes = DataSourceTransactionManager.class)
public class SpringJdbcTransactionOperations implements TransactionOperations<Connection> {

    private final TransactionTemplate writeTransactionTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final DataSource dataSource;

    /**
     * Default constructor.
     * @param transactionManager The transaction manager
     */
    protected SpringJdbcTransactionOperations(
            DataSourceTransactionManager transactionManager) {
        this.dataSource = transactionManager.getDataSource();
        this.writeTransactionTemplate = new TransactionTemplate(transactionManager);
        DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition();
        transactionDefinition.setReadOnly(true);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager, transactionDefinition);
    }

    @Override
    public <R> R executeRead(@NonNull Function<TransactionStatus<Connection>, R> callback) {
        return writeTransactionTemplate.execute(status -> callback.apply(new JdbcTransactionStatus(status)));
    }

    @Override
    public <R> R executeWrite(@NonNull Function<TransactionStatus<Connection>, R> callback) {
        return readTransactionTemplate.execute(status -> callback.apply(new JdbcTransactionStatus(status)));
    }

    @NonNull
    @Override
    public Connection getConnection() {
        try {
            Connection connection = DataSourceUtils.doGetConnection(dataSource);
            if (DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                return connection;
            } else {
                connection.close();
                throw new NoTransactionException("No transaction declared. Define @Transactional on the surrounding method prior to calling getConnection()");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Error retrieving JDBC connection: " + e.getMessage(), e);
        }
    }

    /**
     * Internal transaction status.
     */
    private final class JdbcTransactionStatus implements TransactionStatus<Connection> {

        private final org.springframework.transaction.TransactionStatus springStatus;

        JdbcTransactionStatus(org.springframework.transaction.TransactionStatus springStatus) {
            this.springStatus = springStatus;
        }

        @Override
        public boolean isNewTransaction() {
            return springStatus.isNewTransaction();
        }

        @Override
        public void setRollbackOnly() {
            springStatus.setRollbackOnly();
        }

        @Override
        public boolean isRollbackOnly() {
            return springStatus.isRollbackOnly();
        }

        @Override
        public boolean isCompleted() {
            return springStatus.isCompleted();
        }

        @Override
        public boolean hasSavepoint() {
            return springStatus.hasSavepoint();
        }

        @Override
        public void flush() {
            springStatus.flush();
        }

        @NonNull
        @Override
        public Object getTransaction() {
            return springStatus;
        }

        @NonNull
        @Override
        public Connection getConnection() {
            return SpringJdbcTransactionOperations.this.getConnection();
        }

        @Override
        public Object createSavepoint() throws TransactionException {
            return springStatus.createSavepoint();
        }

        @Override
        public void rollbackToSavepoint(Object savepoint) throws TransactionException {
            springStatus.rollbackToSavepoint(savepoint);
        }

        @Override
        public void releaseSavepoint(Object savepoint) throws TransactionException {
            springStatus.releaseSavepoint(savepoint);
        }
    }
}
