#!/bin/bash
sleep 3
mysql -uroot -pgrafana123456 -e “grant all privileges on grafana.* to ‘root’@‘%’ identified by ‘grafana’;”
sleep 2
mysql -uroot -pgrafana123456 -e “flush privileges;”
