# syntax=docker/dockerfile:1

FROM python:3.8.13-alpine
RUN mkdir /app
# The localRequirements.txt file is a requirements.txt formatted file specfiying any local package installations
# desired.  This may be necessary if, for example, the python code this connector may execute requires additional
# python packages installed (e.g., tensorflow).
#
# If no additional packages are required, this file should be empty
COPY localRequirements.txt /app
WORKDIR /app
RUN pip install -i https://test.pypi.org/simple --extra-index-url https://pypi.org/simple/ vantiqPythonExecConnector==0.9.1
RUN pip install -r localRequirements.txt
ENTRYPOINT ["vantiqPythonExecConnector"]