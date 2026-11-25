-- synthetic migration rehearsal only.
insert into sys_user(id,username,password_hash,real_name,role) values (1,'migration_student','placeholder','Synthetic student','STUDENT'),(2,'migration_repairer','placeholder','Synthetic repairer','REPAIRER');
insert into repair_order(id,student_id,repairer_id,title,description,location,status) values (1,1,null,'Migration fixture','Synthetic','TEST','REJECTED'),(2,1,2,'Migration fixture','Synthetic','TEST','COMPLETED'),(3,1,2,'Migration fixture','Synthetic','TEST','COMPLETED');
insert into repair_record(order_id,repairer_id,description) values (3,2,'Synthetic record retained');
select setval('sys_user_id_seq',2);
select setval('repair_order_id_seq',3);
