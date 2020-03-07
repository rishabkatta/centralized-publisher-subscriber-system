**Architecture and Interaction Diagrams** 
<br/>
* The Architecture Diagram gives you an overview at a High level on what's going on in the project. 
* The Interaction diagram gives you a little deeper insight into how each publisher, Subscriber, and Event Manager handles each operation.

**DOCKER COMPOSE EXECUTION**

* cd into downloaded repository.
* run "docker-compose -f docker-compose-project2.yml up --build -d"
* exec into each docker container in a seperate terminal 
  > "docker exec -it eventmanager bash" <br/>
  > "docker exec -it publisher1 bash"
                                                            
* cd project2/
* run "java -cp target/project2-1.0.jar edu.rit.cs.EventManager" in eventmanager container
* run "java -cp target/project2-1.0.jar edu.rit.cs.PublisherAgent < eventmanagerIP > 9091" in publisher containers
* run "java -cp target/project2-1.0.jar edu.rit.cs.SubscriberAgent < eventmanagerIP > 9091" 
* Then try and play with the pub-sub system by giving appropriate answers to the CLI questions.

**eventmanagerIP is displayed after you run "java -cp target/project2-1.0.jar edu.rit.cs.EventManager" in eventmanager container**

**DOCKER EXECUTION**
* cd into downloaded repository.
* run "docker build -t project2:rishabkatta -f Dockerfile-project2 ."   
* run "docker run --hostname eventmanager -it project2:rishabkatta /bin/bash"
* run "docker run --hostname publisher1 -it project2:rishabkatta /bin/bash" in another terminal.
* run "docker run --hostname subscriber1 -it project2:rishabkatta /bin/bash" in another.
* In eventmanager container, run "java -cp target/project2-1.0.jar edu.rit.cs.EventManager"
* In publisher container run "java -cp target/project2-1.0.jar edu.rit.cs.PublisherAgent < eventmanagerIP > 9091"
* In subscriber container run "java -cp target/project2-1.0.jar edu.rit.cs.SubscriberAgent < eventmanagerIP > 9091"

                                           

