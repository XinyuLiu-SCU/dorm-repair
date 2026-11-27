package com.dormrepair.workflow;
/** This V2 branch never defaults or redirects regression writes to the V1.1 integration database. */
final class WorkflowTestTargets {
 static String database(){return checked(System.getenv().getOrDefault("FLOWABLE_TEST_DB_NAME","dorm_repair_v2_test_v1"));}
 static String rehearsal(){return checked(System.getenv().getOrDefault("FLOWABLE_REHEARSAL_DB_NAME","dorm_repair_v2_test_rehearsal"));}
 static String primaryUrl(){return "jdbc:postgresql://localhost:5432/"+database();}
 static String rehearsalUrl(){return "jdbc:postgresql://localhost:5432/"+rehearsal();}
 static String checked(String name){if(name==null||!name.matches("dorm_repair_v2_test(?:_[a-z0-9]+)?"))throw new IllegalStateException("This branch requires a V2-isolated regression database");return name;}
}
