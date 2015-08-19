# Dictionary Creator

Create a dictionary with inverse document frequency (idf) values from the 
[Google NGrams] dataset.

## Usage

Download the 1-gram files and the *total counts* files for your language from
the [Google NGrams] site into a common folder.

Compile using Maven:

    mvn compile assembly:single

Run as:

    java -jar target/dictionary-creator-0.0.1-SNAPSHOT-jar-with-dependencies.jar $ngrams_folder $output_file

Run the program without arguments to see the other parameters.


## License

Copyright 2015 Gerhard Gossen. This program may be used under the Apache License 2.0.




[Google NGrams]: http://storage.googleapis.com/books/ngrams/books/datasetsv2.html
