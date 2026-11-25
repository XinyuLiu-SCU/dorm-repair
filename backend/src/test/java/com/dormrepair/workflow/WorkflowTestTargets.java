package com.dormrepair.workflow;
/** Shared test-only allow-list; existing V1 defaults remain supported. */
final class WorkflowTestTargets {
    static String database(){return checked(System.getenv().getOrDefault("FLOWABLE_TEST_DB_NAME","dorm_repair_flowable_a_test_20261123"),"dorm_repair_flowable_a_test_20261123");}
    static String rehearsal(){return checked(System.getenv().getOrDefault("FLOWABLE_REHEARSAL_DB_NAME","dorm_repair_migration_test_20261123_d"),"dorm_repair_migration_test_20261123_d");}
    static String primaryUrl(){return url(database());}
    static String rehearsalUrl(){return url(rehearsal());}
    private static String url(String name){return "jdbc:postgresql://localhost:5432/"+name;}
    private static String checked(String name,String legacy){if(!name.equals(legacy)&&!name.matches("dorm_repair_v2_test(?:_[a-z0-9]+)?"))throw new IllegalStateException("Regression tests require an explicitly isolated database");return name;}
}
