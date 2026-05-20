UPDATE language SET image = 'ocee/sandbox-c:13'    WHERE name = 'c-gcc'   AND version = '13';
UPDATE language SET image = 'ocee/sandbox-cpp:13'  WHERE name = 'cpp-gcc' AND version = '13';
UPDATE language SET image = 'ocee/sandbox-node:20' WHERE name = 'node'    AND version = '20';
UPDATE language SET is_active = TRUE WHERE name IN ('c-gcc','cpp-gcc','node');
