#!/bin/bash
sleep 3
mysql -uroot -ppetclinic -e “grant all privileges on grafana.* to ‘root’@‘%’ identified by ‘grafana’;”
sleep 2
mysql -uroot -ppetclinic -e “flush privileges;”
