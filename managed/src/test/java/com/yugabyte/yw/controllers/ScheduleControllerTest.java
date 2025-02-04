// Copyright (c) YugaByte, Inc.

package com.yugabyte.yw.controllers;

import static com.yugabyte.yw.common.AssertHelper.assertAuditEntry;
import static com.yugabyte.yw.common.AssertHelper.assertBadRequest;
import static com.yugabyte.yw.common.AssertHelper.assertOk;
import static com.yugabyte.yw.common.AssertHelper.assertYWSE;
import static org.junit.Assert.assertEquals;
import static play.mvc.Http.Status.FORBIDDEN;
import static play.test.Helpers.contentAsString;

import com.fasterxml.jackson.databind.JsonNode;
import com.yugabyte.yw.common.FakeApiHelper;
import com.yugabyte.yw.common.FakeDBApplication;
import com.yugabyte.yw.common.ModelFactory;
import com.yugabyte.yw.forms.BackupTableParams;
import com.yugabyte.yw.models.Customer;
import com.yugabyte.yw.models.CustomerConfig;
import com.yugabyte.yw.models.Schedule;
import com.yugabyte.yw.models.Universe;
import com.yugabyte.yw.models.Users;
import com.yugabyte.yw.models.helpers.TaskType;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import play.libs.Json;
import play.mvc.Result;

public class ScheduleControllerTest extends FakeDBApplication {

  private Universe defaultUniverse;
  private Customer defaultCustomer;
  private Users defaultUser;
  private Schedule defaultSchedule;

  @Before
  public void setUp() {
    defaultCustomer = ModelFactory.testCustomer();
    defaultUser = ModelFactory.testUser(defaultCustomer);
    defaultUniverse = ModelFactory.createUniverse(defaultCustomer.getCustomerId());

    BackupTableParams backupTableParams = new BackupTableParams();
    backupTableParams.universeUUID = defaultUniverse.universeUUID;
    CustomerConfig customerConfig = ModelFactory.createS3StorageConfig(defaultCustomer);
    backupTableParams.storageConfigUUID = customerConfig.configUUID;
    defaultSchedule =
        Schedule.create(defaultCustomer.uuid, backupTableParams, TaskType.BackupUniverse, 1000);
  }

  private Result listSchedules(UUID customerUUID) {
    String authToken = defaultUser.createAuthToken();
    String method = "GET";
    String url = "/api/customers/" + customerUUID + "/schedules";

    return FakeApiHelper.doRequestWithAuthToken(method, url, authToken);
  }

  private Result deleteSchedule(UUID scheduleUUID, UUID customerUUID) {
    String authToken = defaultUser.createAuthToken();
    String method = "DELETE";
    String url = "/api/customers/" + customerUUID + "/schedules/" + scheduleUUID;

    return FakeApiHelper.doRequestWithAuthToken(method, url, authToken);
  }

  @Test
  public void testListWithValidCustomer() {
    Result r = listSchedules(defaultCustomer.uuid);
    assertOk(r);
    JsonNode resultJson = Json.parse(contentAsString(r));
    assertEquals(1, resultJson.size());
    assertEquals(
        resultJson.get(0).get("scheduleUUID").asText(), defaultSchedule.scheduleUUID.toString());
    assertAuditEntry(0, defaultCustomer.uuid);
  }

  @Test
  public void testListWithInvalidCustomer() {
    UUID invalidCustomerUUID = UUID.randomUUID();
    Result r = listSchedules(invalidCustomerUUID);
    assertEquals(FORBIDDEN, r.status());
    String resultString = contentAsString(r);
    assertEquals(resultString, "Unable To Authenticate User");
    assertAuditEntry(0, defaultCustomer.uuid);
  }

  @Test
  public void testDeleteValid() {
    JsonNode resultJson = Json.parse(contentAsString(listSchedules(defaultCustomer.uuid)));
    assertEquals(1, resultJson.size());
    Result r = deleteSchedule(defaultSchedule.scheduleUUID, defaultCustomer.uuid);
    assertOk(r);
    resultJson = Json.parse(contentAsString(listSchedules(defaultCustomer.uuid)));
    assertEquals(0, resultJson.size());
    assertAuditEntry(1, defaultCustomer.uuid);
  }

  @Test
  public void testDeleteInvalidCustomerUUID() {
    UUID invalidCustomerUUID = UUID.randomUUID();
    JsonNode resultJson = Json.parse(contentAsString(listSchedules(defaultCustomer.uuid)));
    assertEquals(1, resultJson.size());
    Result r = deleteSchedule(defaultSchedule.scheduleUUID, invalidCustomerUUID);
    assertEquals(FORBIDDEN, r.status());
    String resultString = contentAsString(r);
    assertEquals(resultString, "Unable To Authenticate User");
    resultJson = Json.parse(contentAsString(listSchedules(defaultCustomer.uuid)));
    assertEquals(1, resultJson.size());
    assertAuditEntry(0, defaultCustomer.uuid);
  }

  @Test
  public void testDeleteInvalidScheduleUUID() {
    UUID invalidScheduleUUID = UUID.randomUUID();
    JsonNode resultJson = Json.parse(contentAsString(listSchedules(defaultCustomer.uuid)));
    assertEquals(1, resultJson.size());
    Result result = assertYWSE(() -> deleteSchedule(invalidScheduleUUID, defaultCustomer.uuid));
    assertBadRequest(result, "Invalid Schedule UUID: " + invalidScheduleUUID);
    resultJson = Json.parse(contentAsString(listSchedules(defaultCustomer.uuid)));
    assertEquals(1, resultJson.size());
    assertAuditEntry(0, defaultCustomer.uuid);
  }
}
