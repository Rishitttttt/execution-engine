UPDATE language SET image = 'ocee/sandbox-python:3.11'
 WHERE name = 'python3' AND version = '3.11';

UPDATE language SET image = 'ocee/sandbox-java:21'
 WHERE name = 'java-openjdk' AND version = '21';

UPDATE language SET is_active = FALSE
 WHERE name IN ('c-gcc', 'cpp-gcc', 'node');

ALTER TABLE language ALTER COLUMN image DROP DEFAULT;
