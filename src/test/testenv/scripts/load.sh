for f in ../sampledata/W*.json
do
    curl -X POST -H "Content-Type: application/json" -d @$f http://localhost:8090/fhir/
    echo Done with $f
done
