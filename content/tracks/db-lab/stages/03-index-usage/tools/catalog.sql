USE lab;
CREATE TABLE digit(n INT PRIMARY KEY);
INSERT INTO digit VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);
CREATE TABLE products(
  id BIGINT PRIMARY KEY, sku VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  tenant_id INT NOT NULL, category_id INT NOT NULL, created_at DATETIME NOT NULL, price INT NOT NULL
);
INSERT INTO products
SELECT n, IF(n=999999,'4321',LPAD(n,12,'0')), FLOOR(n/200)%100, n%200,
  TIMESTAMPADD(SECOND,FLOOR(n/1000),TIMESTAMPADD(DAY,n%1000,'2024-01-01')), n%100000
FROM (SELECT 1+a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n AS n
  FROM digit a CROSS JOIN digit b CROSS JOIN digit c CROSS JOIN digit d CROSS JOIN digit e CROSS JOIN digit f
) numbers ORDER BY n;
CREATE UNIQUE INDEX idx_sku ON products(sku);
CREATE INDEX idx_created ON products(created_at,id);
CREATE INDEX idx_tenant_category ON products(tenant_id,category_id,id);
CREATE INDEX idx_price ON products(price,id);
UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME LIKE 'events_statements%';
