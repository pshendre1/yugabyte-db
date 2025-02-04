// Copyright (c) YugaByte, Inc.

package com.yugabyte.yw.models;

import static com.yugabyte.yw.models.helpers.CommonUtils.DB_OR_CHAIN_TO_WARN;
import static com.yugabyte.yw.models.helpers.CommonUtils.appendInClause;
import static com.yugabyte.yw.models.helpers.CommonUtils.nowWithoutMillis;
import static com.yugabyte.yw.models.helpers.CommonUtils.setUniqueListValue;
import static com.yugabyte.yw.models.helpers.CommonUtils.setUniqueListValues;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yugabyte.yw.models.filters.MetricFilter;
import com.yugabyte.yw.models.helpers.KnownAlertLabels;
import io.ebean.ExpressionList;
import io.ebean.Finder;
import io.ebean.Junction;
import io.ebean.Model;
import io.prometheus.client.Collector;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

@Entity
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Slf4j
@Access(AccessType.PROPERTY)
public class Metric extends Model {

  // For now only support gauge.
  public enum Type {
    GAUGE(Collector.Type.GAUGE);

    private final Collector.Type prometheusType;

    Type(Collector.Type prometheusType) {
      this.prometheusType = prometheusType;
    }

    public Collector.Type getPrometheusType() {
      return prometheusType;
    }
  }

  @Id private UUID uuid;

  @Column private UUID customerUUID;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  private Type type;

  @Column(nullable = false)
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
  private Date createTime = nowWithoutMillis();

  @Column(nullable = false)
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
  private Date updateTime = nowWithoutMillis();

  @Column(nullable = false)
  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
  private Date expireTime;

  @Column(nullable = false)
  private UUID targetUuid;

  @OneToMany(mappedBy = "metric", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<MetricLabel> labels;

  @Column(nullable = false)
  private Double value;

  private static final Finder<UUID, Metric> find = new Finder<UUID, Metric>(Metric.class) {};

  @JsonIgnore
  public boolean isNew() {
    return uuid == null;
  }

  public Metric setUuid(UUID uuid) {
    this.uuid = uuid;
    this.labels.forEach(label -> label.setMetric(this));
    return this;
  }

  public Metric generateUUID() {
    return setUuid(UUID.randomUUID());
  }

  public String getLabelValue(KnownAlertLabels knownLabel) {
    return getLabelValue(knownLabel.labelName());
  }

  public String getLabelValue(String name) {
    return labels
        .stream()
        .filter(label -> name.equals(label.getName()))
        .map(MetricLabel::getValue)
        .findFirst()
        .orElse(null);
  }

  public Metric setLabel(KnownAlertLabels label, String value) {
    return setLabel(label.labelName(), value);
  }

  public Metric setLabel(String name, String value) {
    MetricLabel toAdd = new MetricLabel(this, name, value);
    this.labels = setUniqueListValue(labels, toAdd);
    return this;
  }

  public Metric setLabels(List<MetricLabel> labels) {
    this.labels = setUniqueListValues(this.labels, labels);
    this.labels.forEach(label -> label.setMetric(this));
    return this;
  }

  public List<MetricLabel> getLabels() {
    return labels
        .stream()
        .sorted(Comparator.comparing(MetricLabel::getName))
        .collect(Collectors.toList());
  }

  public static ExpressionList<Metric> createQueryByFilter(MetricFilter filter) {
    ExpressionList<Metric> query = find.query().fetch("labels").where();
    appendInClause(query, "uuid", filter.getUuids());
    if (filter.getCustomerUuid() != null) {
      query.eq("customerUUID", filter.getCustomerUuid());
    }
    if (filter.getTargetUuid() != null) {
      query.eq("targetUuid", filter.getTargetUuid());
    }
    if (!CollectionUtils.isEmpty(filter.getKeys())) {
      if (filter.getKeys().size() > DB_OR_CHAIN_TO_WARN) {
        log.warn("Querying for {} metric keys - may affect performance", filter.getKeys().size());
      }
      Junction<Metric> orExpr = query.or();
      for (MetricKey key : filter.getKeys()) {
        Junction<Metric> andExpr = orExpr.and();
        andExpr.eq("name", key.getName());
        if (key.getCustomerUuid() != null) {
          andExpr.eq("customerUUID", key.getCustomerUuid());
        } else {
          andExpr.isNull("customerUUID");
        }
        if (key.getTargetUuid() != null) {
          andExpr.eq("targetUuid", key.getTargetUuid());
        } else {
          andExpr.isNull("targetUuid");
        }
        orExpr.endAnd();
      }
      query.endOr();
    }
    if (filter.getExpired() != null) {
      if (filter.getExpired()) {
        query.lt("expireTime", nowWithoutMillis());
      } else {
        query.gt("expireTime", nowWithoutMillis());
      }
    }
    return query;
  }
}
