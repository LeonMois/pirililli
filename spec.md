The application is a fun little gimmick for a party, that will run fully locally on the machine. The idea is a voting machine in the style of the brazilian voting system for "achievements" of the year. Things like best x or best y. 
There should be a set of questions and eligible candidates configurable via a spreadsheet. There is also a list of all candidates with a number, name and picture assignable to them. Ideally the list is also kept in a spreadsheet, and each candidate needs to somehow also have a picture assigend to them.  
An application that looks like the voting machines in brazil, see the electronig_voting_device.jpg

The functionality is as follows:
Login page, a valid "voter id" needs to be provided. This is a pre-generated 6 digit number. There need to be a configurable amount if  numbers generated 
After logging in, the ui based on the electronig_voting_device.jpg is shown. There  is a slight modification, that above the persons picture is the question for which to vote
While no candidate is selected the picture field is empty and the question stays on the top, the whole layout doesnt shift.
When the voter selects a 2 digit number for the candidate, their picture is shown and they are selected.  When they press the reset button the selection is gone and the number as well as the picture disappear. 
When the voter presses submit, the vote is counted for that question the iconic pirililli sound of the voting machine chimes and the next question appears. After the last question is answered a page with thanks for your voting appears and after 10 seconds it will return to the log in page. The voter cant vote with their number again. 
The voters are also assigned a gringo status, if the person is a gringo, then the application immediately takes the english translation instead of the portuguese original. 
The program should be done in java as an own application, to not break the immersion if it is running in the browser. A postgress docker container should be used to store information. Every time the programm is restarted, it resets all the voting, but the previous voting should still be retrievable from the database. 

