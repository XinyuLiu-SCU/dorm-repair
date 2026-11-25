-- synthetic fixture for a brand-new isolated database, never production.
insert into sys_user(username,password_hash,real_name,role) values ('stage_b_legacy','placeholder','Legacy fixture','STUDENT');
insert into repair_order(student_id,title,description,location,status) select id,'legacy fixture','before workflow migration','test','COMPLETED' from sys_user where username='stage_b_legacy';
