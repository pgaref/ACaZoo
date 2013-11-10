#!/bin/bash

rm -r /var/zootmp/*
echo "1" > /var/zootmp/myid
rm -r /var/lib/cassandra/*
ant build
