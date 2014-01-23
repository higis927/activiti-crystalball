package org.activiti.crystalball.simulator;

import org.activiti.crystalball.simulator.delegate.event.*;
import org.activiti.crystalball.simulator.impl.DefaultSimulationProcessEngineFactory;
import org.activiti.crystalball.simulator.impl.EventRecorderTestUtils;
import org.activiti.crystalball.simulator.delegate.event.impl.ProcessInstanceCreateTransformer;
import org.activiti.crystalball.simulator.delegate.event.impl.RecordActivitiEventListener;
import org.activiti.crystalball.simulator.delegate.event.impl.UserTaskCompleteTransformer;
import org.activiti.crystalball.simulator.impl.RecordableProcessEngineFactory;
import org.activiti.crystalball.simulator.impl.StartProcessEventHandler;
import org.activiti.crystalball.simulator.impl.playback.PlaybackUserTaskCompleteEventHandler;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.util.ClockUtil;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class SimpleSimulationRunTest {
  // Process instance start event
  private static final String PROCESS_INSTANCE_START_EVENT_TYPE = "PROCESS_INSTANCE_START";
  private static final String PROCESS_DEFINITION_ID_KEY = "processDefinitionId";
  private static final String VARIABLES_KEY = "variables";
  // User task completed event
  private static final String USER_TASK_COMPLETED_EVENT_TYPE = "USER_TASK_COMPLETED";

  private static final String BUSINESS_KEY = "testBusinessKey";

  public static final String TEST_VALUE = "TestValue";
  public static final String TEST_VARIABLE = "testVariable";

  private static final String USERTASK_PROCESS = "org/activiti/crystalball/simulator/impl/playback/PlaybackProcessStartTest.testUserTask.bpmn20.xml";

  protected RecordActivitiEventListener listener;

  @Before
  public void initListener() {
    listener = new RecordActivitiEventListener(ExecutionEntity.class, getTransformers());
  }

  @After
  public void cleanupListener() {
    listener = null;
  }

  @Test
  public void testStep() throws Exception {

    recordEvents();

    SimulationDebugger simDebugger = createDebugger();

    simDebugger.init();

    RuntimeService runtimeService = SimulationRunContext.getRuntimeService();
    TaskService taskService = SimulationRunContext.getTaskService();
    HistoryService historyService = SimulationRunContext.getHistoryService();

    // debuger step - start process and stay on the userTask
    simDebugger.step();
    step1Check(runtimeService, taskService);

    // debugger step - complete userTask and finish process
    simDebugger.step();
    step2Check(runtimeService, taskService);

    checkStatus(historyService);

    simDebugger.close();
    ProcessEngines.destroy();
  }

  private void step2Check(RuntimeService runtimeService, TaskService taskService) {ProcessInstance procInstance = runtimeService.createProcessInstanceQuery().active().processInstanceBusinessKey("oneTaskProcessBusinessKey").singleResult();
    assertNull(procInstance);
    Task t = taskService.createTaskQuery().active().taskDefinitionKey("userTask").singleResult();
    assertNull(t);
  }

  @Test
  public void testRunToTime() throws Exception {

    recordEvents();

    SimulationDebugger simDebugger = createDebugger();

    simDebugger.init();

    RuntimeService runtimeService = SimulationRunContext.getRuntimeService();
    TaskService taskService = SimulationRunContext.getTaskService();
    HistoryService historyService = SimulationRunContext.getHistoryService();

    simDebugger.runTo(0);
    ProcessInstance procInstance = runtimeService.createProcessInstanceQuery().active().processInstanceBusinessKey("oneTaskProcessBusinessKey").singleResult();
    assertNull(procInstance);

    // debuger step - start process and stay on the userTask
    simDebugger.runTo(1);
    step1Check(runtimeService, taskService);

    // process engine should be in the same state as before
    simDebugger.runTo(1000);
    step1Check(runtimeService, taskService);

    // debugger step - complete userTask and finish process
    simDebugger.runTo(1500);
    step2Check(runtimeService, taskService);

    checkStatus(historyService);

    simDebugger.close();
    ProcessEngines.destroy();
  }

  @Test(expected = RuntimeException.class )
  public void testRunToTimeInThePast() throws Exception {

    recordEvents();
    SimulationDebugger simDebugger = createDebugger();
    simDebugger.init();
    try {
      simDebugger.runTo(-1);
      fail("RuntimeException expected - unable to execute event from the past");
    } finally {
      simDebugger.close();
      ProcessEngines.destroy();
    }
  }

  @Test
  public void testRunToEvent() throws Exception {

    recordEvents();
    SimulationDebugger simDebugger = createDebugger();
    simDebugger.init();
    try {
      simDebugger.runTo(USER_TASK_COMPLETED_EVENT_TYPE);
      step1Check(SimulationRunContext.getRuntimeService(), SimulationRunContext.getTaskService());
      simDebugger.runContinue();
    } finally {
      simDebugger.close();
      ProcessEngines.destroy();
    }
  }

  @Test(expected = RuntimeException.class)
  public void testRunToNonExistingEvent() throws Exception {

    recordEvents();
    SimulationDebugger simDebugger = createDebugger();
    simDebugger.init();
    try {
      simDebugger.runTo("");
      checkStatus(SimulationRunContext.getHistoryService());
    } finally {
      simDebugger.close();
      ProcessEngines.destroy();
    }
  }

  private void step1Check(RuntimeService runtimeService, TaskService taskService) {ProcessInstance procInstance;
    procInstance = runtimeService.createProcessInstanceQuery().active().processInstanceBusinessKey("oneTaskProcessBusinessKey").singleResult();
    assertNotNull(procInstance);
    Task t = taskService.createTaskQuery().active().taskDefinitionKey("userTask").singleResult();
    assertNotNull(t);
  }


  @Test
  public void testRunContinue() throws Exception {
    recordEvents();

    SimulationDebugger simDebugger = createDebugger();

    simDebugger.init();
    simDebugger.runContinue();
    checkStatus(SimulationRunContext.getHistoryService());

    simDebugger.close();
    ProcessEngines.destroy();
  }

  private SimulationDebugger createDebugger() {
    final SimpleSimulationRun.Builder builder = new SimpleSimulationRun.Builder();
    // init simulation run
    DefaultSimulationProcessEngineFactory simulationProcessEngineFactory = new DefaultSimulationProcessEngineFactory(USERTASK_PROCESS);
    builder.processEngineFactory(simulationProcessEngineFactory)
      .eventCalendarFactory(new PlaybackEventCalendarFactory(new SimulationEventComparator(), listener.getSimulationEvents()))
      .customEventHandlerMap(getHandlers());
    return builder.build();
  }

  private void checkStatus(HistoryService historyService) {
    final HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery().
      finished().
      singleResult();
    assertNotNull(historicProcessInstance);
    assertEquals("oneTaskProcessBusinessKey", historicProcessInstance.getBusinessKey());
    HistoricTaskInstance historicTaskInstance = historyService.createHistoricTaskInstanceQuery().taskDefinitionKey("userTask").singleResult();
    assertEquals("user1", historicTaskInstance.getAssignee());
  }

  private void recordEvents() {
    ClockUtil.setCurrentTime(new Date(0));
    ProcessEngine processEngine = (new RecordableProcessEngineFactory(USERTASK_PROCESS, listener))
      .getObject();
    TaskService taskService = processEngine.getTaskService();

    Map<String, Object> variables = new HashMap<String, Object>();
    variables.put(TEST_VARIABLE, TEST_VALUE);
    processEngine.getRuntimeService().startProcessInstanceByKey("oneTaskProcess", "oneTaskProcessBusinessKey", variables);
    EventRecorderTestUtils.increaseTime();
    Task task = taskService.createTaskQuery().taskDefinitionKey("userTask").singleResult();
    taskService.complete(task.getId());
    checkStatus(processEngine.getHistoryService());
    EventRecorderTestUtils.closeProcessEngine(processEngine, listener);
    ProcessEngines.destroy();
  }

  private List<ActivitiEventToSimulationEventTransformer> getTransformers() {
    List<ActivitiEventToSimulationEventTransformer> transformers = new ArrayList<ActivitiEventToSimulationEventTransformer>();
    transformers.add(new ProcessInstanceCreateTransformer(PROCESS_INSTANCE_START_EVENT_TYPE, PROCESS_DEFINITION_ID_KEY, BUSINESS_KEY, VARIABLES_KEY));
    transformers.add(new UserTaskCompleteTransformer(USER_TASK_COMPLETED_EVENT_TYPE));
    return transformers;
  }

  public static Map<String, SimulationEventHandler> getHandlers() {
    Map<String, SimulationEventHandler> handlers = new HashMap<String, SimulationEventHandler>();
    handlers.put(PROCESS_INSTANCE_START_EVENT_TYPE, new StartProcessEventHandler(PROCESS_DEFINITION_ID_KEY, BUSINESS_KEY, VARIABLES_KEY));
    handlers.put(USER_TASK_COMPLETED_EVENT_TYPE, new PlaybackUserTaskCompleteEventHandler());
    return handlers;
  }
}
