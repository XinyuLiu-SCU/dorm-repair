package com.dormrepair.workflow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkflowTestTargetsSafetyTest {
 @Test void protectedAndLegacyTargetsAreRejected(){for(String db:new String[]{"dorm_repair_db","dorm_repair_flowable_a_test_20261123","dorm_repair_migration_test_20261123_d","dorm_repair_v2_dev","postgres"})assertThrows(IllegalStateException.class,()->WorkflowTestTargets.checked(db));}
 @Test void defaultsAndExplicitTargetsStayInV2TestNamespace(){assertTrue(WorkflowTestTargets.database().startsWith("dorm_repair_v2_test"));assertTrue(WorkflowTestTargets.rehearsal().startsWith("dorm_repair_v2_test"));assertEquals("dorm_repair_v2_test_v1",WorkflowTestTargets.checked("dorm_repair_v2_test_v1"));}
}
