package com.netflix.spinnaker.clouddriver.helpers;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.List;

public class EnableDisablePercentageCategorizer {

  /**
   * During an enable/disable operation that accepts a desired percentage of instances to leave enabled/disabled, this
   * acts as a helper function to return which instances still need to be enabled/disabled.
   *
   * NOTE: modified + unmodified should be the total list of instances managed by one server group
   *
   * @param modified          are the instances that don't need to be enabled/disabled (presumably have already been
   *                          enabled/disabled).
   * @param unmodified        are the instances that do need to be enabled/disabled.
   * @param desiredPercentage is the end desired percentage.
   * @return the list of instances to be enabled/disabled. If the percentage has already been achieved or exceeded by
   * the input instances, we return an empty list.
   */
  public static <T> List<T> getInstancesToModify(List<T> modified, List<T> unmodified, int desiredPercentage) {
    if (desiredPercentage < 0 || desiredPercentage > 100) {
      throw new RuntimeException("Desired target percentage must be between 0 and 100 inclusive");
    }

    int totalSize = modified.size() + unmodified.size();
    int newSize = (int) Math.ceil(totalSize * (float) (desiredPercentage / 100));

    int returnSize = modified.size() > newSize ? 0 : newSize - modified.size();

    return DefaultGroovyMethods.take(unmodified, returnSize);
  }
}
