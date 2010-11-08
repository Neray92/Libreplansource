/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.montecarlo;

import static org.navalplanner.web.I18nHelper._;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joda.time.LocalDate;
import org.navalplanner.business.orders.entities.Order;
import org.navalplanner.web.common.Util;
import org.navalplanner.web.planner.allocation.streches.IStretchesFunctionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.SuspendNotAllowedException;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.GenericForwardComposer;
import org.zkoss.zul.Button;
import org.zkoss.zul.Decimalbox;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Intbox;
import org.zkoss.zul.Label;
import org.zkoss.zul.Row;
import org.zkoss.zul.RowRenderer;
import org.zkoss.zul.Rows;
import org.zkoss.zul.SimpleListModel;
import org.zkoss.zul.XYModel;
import org.zkoss.zul.api.Window;

/**
 * Controller for MonteCarlo graphic generation
 *
 * @author Diego Pino Garcia <dpino@igalia.com>
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class MonteCarloController extends GenericForwardComposer {

    @Autowired
    private IMonteCarloModel monteCarloModel;

    private final Integer DEFAULT_ITERATIONS = Integer.valueOf(10000);

    private final Integer MAX_NUMBER_ITERATIONS = Integer.valueOf(100000);

    private final RowRenderer gridCriticalPathTasksRender = new CriticalPathTasksRender();

    private Grid gridCriticalPathTasks;

    private Intbox ibIterations;

    private Button btnRunMonteCarlo;

    private Window monteCarloChartWindow;

    public MonteCarloController() {

    }

    @Override
    public void doAfterCompose(org.zkoss.zk.ui.Component comp) throws Exception {
        super.doAfterCompose(comp);
        ibIterations.setValue(DEFAULT_ITERATIONS);
        btnRunMonteCarlo.addEventListener(Events.ON_CLICK, new EventListener() {

            @Override
            public void onEvent(Event event) throws Exception {
                int iterations = getIterations();
                validateRowsPercentages();
                Map<LocalDate, BigDecimal> monteCarloData = monteCarloModel
                        .calculateMonteCarlo(iterations);
                showMonteCarloGraph(monteCarloData);
            }

            private int getIterations() {
                int iterations = ibIterations.getValue() != null ? ibIterations
                        .getValue().intValue() : 0;
                if (iterations == 0) {
                    throw new WrongValueException(ibIterations,
                            _("Cannot be null or empty"));
                }
                if (iterations < 0 || iterations > MAX_NUMBER_ITERATIONS) {
                    throw new WrongValueException(ibIterations,
                            _("Number of iterations should be between 1 and "
                                    + MAX_NUMBER_ITERATIONS));
                }
                return iterations;
            }

            private void validateRowsPercentages() {
                Intbox intbox;

                Rows rows = gridCriticalPathTasks.getRows();
                for (Object each : rows.getChildren()) {
                    Row row = (Row) each;
                    List<Component> children = row.getChildren();

                    Integer sum = 0;
                    intbox = (Intbox) children.get(3);
                    sum += intbox.getValue();
                    intbox = (Intbox) children.get(5);
                    sum += intbox.getValue();
                    intbox = (Intbox) children.get(7);
                    sum += intbox.getValue();

                    if (sum != 100) {
                        throw new WrongValueException(row,
                                _("Percentages should sum 100"));
                    }
                }
            }

            private void showMonteCarloGraph(Map<LocalDate, BigDecimal> data) {
                monteCarloChartWindow = createMonteCarloGraphWindow(data);
                try {
                    monteCarloChartWindow.doModal();
                } catch (SuspendNotAllowedException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            private Window createMonteCarloGraphWindow(
                    Map<LocalDate, BigDecimal> data) {
                HashMap<String, Object> args = new HashMap<String, Object>();
                args.put("monteCarloGraphController",
                        new MonteCarloGraphController());
                Window result = (Window) Executions.createComponents(
                        "/planner/montecarlo_function.zul", self, args);
                MonteCarloGraphController controller = (MonteCarloGraphController) result
                        .getVariable("monteCarloGraphController", true);
                final String orderName = monteCarloModel.getOrderName();
                controller.generateMonteCarloGraph(orderName, data);

                return result;
            }

        });
        reloadGridCritialPathTasks();
    }

    private void reloadGridCritialPathTasks() {
        gridCriticalPathTasks.setModel(new SimpleListModel(
                getCriticalPathTasks()));
        if (gridCriticalPathTasks.getRowRenderer() == null) {
            gridCriticalPathTasks.setRowRenderer(gridCriticalPathTasksRender);
        }
    }

    public List<MonteCarloTask> getCriticalPathTasks() {
        return monteCarloModel.getCriticalPathTasks();
    }

    public void setCriticalPath(Order order, List criticalPath) {
        monteCarloModel.setCriticalPath(order, criticalPath);
    }

    public interface IGraphicGenerator {

        public boolean areChartsEnabled(IStretchesFunctionModel model);

        XYModel getDedicationChart(
                IStretchesFunctionModel stretchesFunctionModel);

        XYModel getAccumulatedHoursChartData(
                IStretchesFunctionModel stretchesFunctionModel);

    }

    private class CriticalPathTasksRender implements RowRenderer {

        @Override
        public void render(Row row, Object data) throws Exception {
            row.setValue(data);

            MonteCarloTask task = (MonteCarloTask) data;

            row.appendChild(taskName(task));
            row.appendChild(duration(task));
            row.appendChild(pessimisticDuration(task));
            row.appendChild(pessimisticDurationPercentage(task));
            row.appendChild(normalDuration(task));
            row.appendChild(normalDurationPercentage(task));
            row.appendChild(optimisticDuration(task));
            row.appendChild(optimisticDurationPercentage(task));
        }

        private Label taskName(final MonteCarloTask task) {
            return new Label(task.getTaskName());
        }

        private Label duration(final MonteCarloTask task) {
            Double duration = Double.valueOf(task.getDuration().doubleValue());
            return new Label(duration.toString());
        }

        private Decimalbox pessimisticDuration(final MonteCarloTask task) {
            Decimalbox result = new Decimalbox();
            Util.bind(result, new Util.Getter<BigDecimal>() {

                @Override
                public BigDecimal get() {
                    return task.getPessimisticDuration();
                }

            }, new Util.Setter<BigDecimal>() {

                @Override
                public void set(BigDecimal value) {
                    task.setPessimisticDuration(value);
                }
            });
            return result;
        }

        private Intbox pessimisticDurationPercentage(
                final MonteCarloTask task) {
            Intbox result = new Intbox();
            Util.bind(result, new Util.Getter<Integer>() {

                @Override
                public Integer get() {
                    return task.getPessimisticDurationPercentage();
                }

            }, new Util.Setter<Integer>() {

                @Override
                public void set(Integer value) {
                    task.setPessimisticDurationPercentage(value);
                }
            });
            return result;
        }

        private Decimalbox normalDuration(final MonteCarloTask task) {
            Decimalbox result = new Decimalbox();
            Util.bind(result, new Util.Getter<BigDecimal>() {

                @Override
                public BigDecimal get() {
                    return task.getNormalDuration();
                }

            }, new Util.Setter<BigDecimal>() {

                @Override
                public void set(BigDecimal value) {
                    task.setNormalDuration(value);
                }
            });
            return result;
        }

        private Intbox normalDurationPercentage(final MonteCarloTask task) {
            Intbox result = new Intbox();
            Util.bind(result, new Util.Getter<Integer>() {

                @Override
                public Integer get() {
                    return task.getNormalDurationPercentage();
                }

            }, new Util.Setter<Integer>() {

                @Override
                public void set(Integer value) {
                    task.setNormalDurationPercentage(value);
                }
            });
            return result;
        }

        private Decimalbox optimisticDuration(final MonteCarloTask task) {
            Decimalbox result = new Decimalbox();
            Util.bind(result, new Util.Getter<BigDecimal>() {

                @Override
                public BigDecimal get() {
                    return task.getOptimisticDuration();
                }

            }, new Util.Setter<BigDecimal>() {

                @Override
                public void set(BigDecimal value) {
                    task.setOptimisticDuration(value);
                }
            });
            return result;
        }

        private Intbox optimisticDurationPercentage(final MonteCarloTask task) {
            Intbox result = new Intbox();
            Util.bind(result, new Util.Getter<Integer>() {

                @Override
                public Integer get() {
                    return task.getOptimisticDurationPercentage();
                }

            }, new Util.Setter<Integer>() {

                @Override
                public void set(Integer value) {
                    task.setOptimisticDurationPercentage(value);
                }
            });
            return result;
        }

    }

}