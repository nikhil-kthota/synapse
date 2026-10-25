-- Line numbers where a relationship occurs in the source file (e.g. each call site of a method).
ALTER TABLE graph_edges ADD COLUMN lines INT[];
