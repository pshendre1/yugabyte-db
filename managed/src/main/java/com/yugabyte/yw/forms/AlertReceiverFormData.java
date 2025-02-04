// Copyright (c) YugaByte, Inc.

package com.yugabyte.yw.forms;

import com.yugabyte.yw.common.alerts.AlertReceiverParams;
import java.util.UUID;
import play.data.validation.Constraints;

public class AlertReceiverFormData {

  public UUID alertReceiverUUID;

  @Constraints.Required() public String name;

  @Constraints.Required() public AlertReceiverParams params;
}
