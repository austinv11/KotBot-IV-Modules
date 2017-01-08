#!/usr/bin/env bash

rm -r libs
mkdir libs
cd ..
cd ./KotBot-IV/Core/

rm -r ./build/libs
./gradlew build

cd ..
cd ..
mv ./KotBot-IV/Core/build/libs ./KotBot-IV-Modules/
