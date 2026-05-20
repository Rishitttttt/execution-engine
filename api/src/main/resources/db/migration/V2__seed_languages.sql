-- Fixed IDs. Real compile/run commands are placeholders for A1; refined in step D.
INSERT INTO language (id, name, version, source_file, compile_command, run_command,
                      default_cpu_time, default_memory, max_cpu_time, max_memory, max_source_size, is_active) VALUES
(1, 'python3',      '3.11',  'main.py',   NULL,                              'python3 main.py',          2.0, 128000,  10.0, 256000, 65536, TRUE),
(2, 'c-gcc',        '13',    'main.c',    'gcc -O2 -o main main.c',          './main',                   2.0, 128000,  10.0, 256000, 65536, TRUE),
(3, 'cpp-gcc',      '13',    'main.cpp',  'g++ -O2 -std=c++20 -o main main.cpp', './main',               2.0, 256000,  10.0, 512000, 65536, TRUE),
(4, 'java-openjdk', '21',    'Main.java', 'javac Main.java',                 'java -Xmx256m Main',       3.0, 256000,  15.0, 512000, 65536, TRUE),
(5, 'node',         '20',    'main.js',   NULL,                              'node main.js',             2.0, 128000,  10.0, 256000, 65536, TRUE);
