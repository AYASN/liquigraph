/*
 * Copyright 2014-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.liquigraph.core.io;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.liquigraph.core.GraphIntegrationTestSuite;
import org.liquigraph.core.exception.ConditionExecutionException;
import org.liquigraph.core.model.AndConditionQuery;
import org.liquigraph.core.model.OrConditionQuery;
import org.liquigraph.core.model.Precondition;
import org.liquigraph.core.model.PreconditionErrorPolicy;
import org.liquigraph.core.model.ConditionQuery;
import org.liquigraph.core.model.SimpleConditionQuery;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.liquigraph.core.io.PatternMatcher.matchesPattern;

public abstract class ConditionExecutorTestSuite implements GraphIntegrationTestSuite {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private ConditionExecutor executor = new ConditionExecutor();

    @Test
    public void executes_simple_precondition() throws SQLException {
        try (Connection connection = graphDatabase().newConnection()) {
            try (Statement ignored = connection.createStatement()) {
                boolean result = executor.executeCondition(
                    connection,
                    simplePrecondition("RETURN true AS result")
                );

                assertThat(result).isTrue();
            }
        }
    }

    @Test
    public void executes_nested_and_precondition_queries() throws SQLException {
        try (Connection connection = graphDatabase().newConnection()) {
            try (Statement ignored = connection.createStatement()) {
                boolean result = executor.executeCondition(
                    connection,
                    andPrecondition("RETURN true AS result", "RETURN false AS result")
                );

                assertThat(result).isFalse();
            }
        }
    }

    @Test
    public void executes_nested_or_precondition_queries() throws SQLException {
        try (Connection connection = graphDatabase().newConnection()) {
            try (Statement ignored = connection.createStatement()) {
                boolean result = executor.executeCondition(
                    connection,
                    orPrecondition("RETURN true AS result", "RETURN false AS result")
                );

                assertThat(result).isTrue();
            }
        }
    }

    @Test
    public void executes_nested_mixed_precondition_queries_like_a_charm() throws SQLException {
        AndConditionQuery andQuery = new AndConditionQuery();
        andQuery.setQueries(Arrays.asList(
            orPreconditionQuery("RETURN false AS result", "RETURN true AS result"),
            simplePreconditionQuery("RETURN true AS result")
        ));
        Precondition precondition = precondition(andQuery);

        try (Connection connection = graphDatabase().newConnection()) {
            try (Statement ignored = connection.createStatement()) {
                boolean result = executor.executeCondition(
                    connection,
                    precondition
                );

                assertThat(result).isTrue();
            }
        }
    }

    @Test
    public void fails_with_invalid_cypher_query() throws SQLException {
        thrown.expect(ConditionExecutionException.class);
        thrown.expectMessage(matchesPattern(String.format(
            "(?ms)%nError executing condition:%n" +
                "\tMake sure your query \\<toto\\> yields exactly one column named or aliased 'result'.%n" +
                "\tActual cause: .*Invalid input 't': expected \\<init\\> \\(line 1, column 1 \\(offset: 0\\)\\)%n" +
                "\"toto\"%n" +
                " \\^.*")));

        try (Connection connection = graphDatabase().newConnection()) {
            try (Statement ignored = connection.createStatement()) {
                executor.executeCondition(
                    connection,
                    simplePrecondition("toto")
                );
            }
        }
    }

    @Test
    public void fails_with_badly_named_precondition_result_column() throws SQLException {
        thrown.expect(ConditionExecutionException.class);
        thrown.expectMessage("Make sure your query <RETURN true> yields exactly one column named or aliased 'result'.");

        try (Connection connection = graphDatabase().newConnection()) {
            try (Statement ignored = connection.createStatement()) {
                executor.executeCondition(
                    connection,
                    simplePrecondition("RETURN true")
                );
            }
        }
    }

    @Test
    public void fails_with_unknown_query_type() throws SQLException {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Unsupported query type <org.liquigraph.core.io.ConditionExecutorTestSuite$1>");

        Precondition precondition = new Precondition();
        precondition.setQuery(new ConditionQuery() {
        });
        try (Connection connection = graphDatabase().newConnection()) {
            executor.executeCondition(connection, precondition);
        }
    }

    private Precondition simplePrecondition(String query) {
        return precondition(simplePreconditionQuery(query));
    }

    private Precondition andPrecondition(String firstQuery, String secondQuery) {
        return precondition(andPreconditionQuery(firstQuery, secondQuery));
    }

    private Precondition orPrecondition(String firstQuery, String secondQuery) {
        return precondition(orPreconditionQuery(firstQuery, secondQuery));
    }

    private AndConditionQuery andPreconditionQuery(String firstQuery, String secondQuery) {
        AndConditionQuery andQuery = new AndConditionQuery();
        andQuery.setQueries(simpleQueries(firstQuery, secondQuery));
        return andQuery;
    }

    private OrConditionQuery orPreconditionQuery(String firstQuery, String secondQuery) {
        OrConditionQuery orQuery = new OrConditionQuery();
        orQuery.setQueries(simpleQueries(firstQuery, secondQuery));
        return orQuery;
    }

    private List<ConditionQuery> simpleQueries(String firstQuery, String secondQuery) {
        return Arrays.asList(
            simplePreconditionQuery(firstQuery),
            simplePreconditionQuery(secondQuery)
        );
    }

    private Precondition precondition(ConditionQuery query) {
        Precondition precondition = new Precondition();
        precondition.setPolicy(PreconditionErrorPolicy.MARK_AS_EXECUTED);
        precondition.setQuery(query);
        return precondition;
    }

    private SimpleConditionQuery simplePreconditionQuery(String query) {
        SimpleConditionQuery simpleQuery = new SimpleConditionQuery();
        simpleQuery.setQuery(query);
        return simpleQuery;
    }
}
