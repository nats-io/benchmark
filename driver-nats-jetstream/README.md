# NATS JetStream benchmarks

This folder houses all of the assets necessary to run benchmarks for [NATS](https://nats.io/) using the [Java NATS client library](https://github.com/nats-io/nats.java). This benchmark tests at-least-once reliable messaging using NATS JetStream. In order to run these benchmarks, you'll need to:

* [Create the necessary local artifacts](#creating-local-artifacts)
* [Stand up a NATS cluster](#creating-a-nats-cluster-on-amazon-web-services-aws-using-terraform-and-ansible) on Amazon Web Services (which includes a client host for running the benchmarks)
* [SSH into the client host](#sshing-into-the-client-host)
* [Run the benchmarks from the client host](#running-the-benchmarks-from-the-client-host)

## Creating local artifacts

In order to create the local artifacts necessary to run the NATS benchmarks in AWS, you'll need to have [Maven](https://maven.apache.org/install.html) installed. Once Maven's installed, you can create the necessary artifacts with a single Maven command:

```bash
# On your worksation...

$ git clone https://github.com/openmessaging/benchmark
$ cd benchmark

# Note: Consider commenting out driver dependencies in benchmark-framework/pom.xml 
# except this one and the Pulsar driver (provides deps used internally by the benchmark harness) to reduce
# the size of the final tarball copied to each worker. Over 15GB without this step!
# https://github.com/openmessaging/benchmark/issues/208

$ mvn install
```

## Creating a NATS cluster on Amazon Web Services (AWS) using Terraform and Ansible

In order to create a NATS cluster on AWS, you'll need to have the following installed on your workstation:

* [Terraform](https://terraform.io)
* [The `terraform-inventory` Ansible adapter for Terraform state](https://github.com/adammck/terraform-inventory)
* [Ansible](http://docs.ansible.com/ansible/latest/intro_installation.html)

> Ansible note: the project playbook requires the Python library `jmespath` installed on local host (a recommended Ansible dependency, but may not be installed by the Ansible installer). 
> Verify this dependency with `pip install jmespath` or `pip3 install jmespath` (Python3)

In addition, you will need to:

* [Create an AWS account](https://aws.amazon.com/account/) (or use an existing account)
* [Install the `aws` CLI tool](https://aws.amazon.com/cli/)
* [Configure the `aws` CLI tool](http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html)

Once those conditions are in place, you'll need to create an SSH public and private key at `~/.ssh/nats_aws` (private) and `~/.ssh/nats_aws.pub` (public), respectively.

```bash
$ ssh-keygen -f ~/.ssh/nats_aws
```

When prompted to enter a passphrase, simply hit **Enter** twice. Then, make sure that the keys have been created:

```bash
$ ls ~/.ssh/nats_aws*
```

### Provision cloud resources with Terraform

With SSH keys in place, you can create the necessary AWS resources using a single Terraform command:

```bash
$ cd driver-nats-jetstream/deploy
$ terraform init
$ terraform apply
```
That will install the following [EC2](https://aws.amazon.com/ec2) instances (plus some other resources, such as a [Virtual Private Cloud](https://aws.amazon.com/vpc/) (VPC)):

When you run `terraform apply`, you will be prompted to type `yes`. Type `yes` to continue with the installation or anything else to quit.

Once the installation is complete, you will see a confirmation message listing the resources that have been installed.

#### Terraform Variables

There are configurable parameters related to the Terraform deployment that you can alter by modifying the defaults in the `terraform.tfvars` file.

Variable | Description | Default
:--------|:------------|:-------
`region` | The AWS region in which the NATS cluster and benchmark workers will be deployed | `us-west-2`
`public_key_path` | The path to the SSH public key that you've generated | `~/.ssh/nats_aws.pub`
`ami` | The [Amazon Machine Image](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AMIs.html) (AWI) to be used by the cluster's machines | `ami-0892d3c7ee96c0bf7` (Ubuntu 20.04 LTS)
`instance_types` | The EC2 instance types used by the various components | `i3.large` (NATS servers), `m6i.large` (benchmarking client)
`num_instances` | The number of EC2 instances of each type to deploy | `2` (NATS servers), `3` (benchmarking client)
> If you modify the `public_key_path`, make sure that you point to the appropriate SSH key path when running the [Ansible playbook](#running-the-ansible-playbook).

### Running the Ansible playbook

Several NATS server configurable parameters in `natsoptions.yaml` are used by the Ansible playbook part of deployment:

Variable | Description | Default
:--------|:------------|:-------
`archiveurl` | NATS Server version and architecture to install | Download URL for NATS Server version 2.6.6, Linux, AMD64
`cliarchiveurl` | NATS CLI utility version and arhitecture to install | Download URL for NATS CLI utility version 0.28, Linux, AMD64
`statemountpath` | The filesystem location the NATS Server will save `jetstream` state | `/state`
`statemounted` | Based on EC2 image type, whether JS state needs to be mounted | `true` (note: only `true` supported)
`statemountsrc` | Based on EC2 image type, name of storage device to mount | `/dev/nvme0n1`
`statefstype` | Filesystem type desired for JS storage device | `ext4`
`maxmemorystore` | Maximum memory that may be used for JS in-memory streams | 2 GB
`maxfilestore` | Maximum disk that may be used for JS file streams | 410 GB
`jsreplicas` | The number of state replicas required by the JS streams | `2`
`purgepreviousstate` | If statemountpath exists, whether to delete any existing files there | `true`

> Note: options such as `statemountsrc` and `maxfilestore` will vary based on your selected EC2 instance type.

With the appropriate infrastructure in place, you can install and start the NATS cluster and benchmark workers using Ansible with just one command:

```bash
$ TF_STATE=./ ansible-playbook \
  --user ubuntu \
  --inventory `which terraform-inventory` \
  deploy.yaml
```

> If you're using an SSH private key path different from `~/.ssh/nats_aws`, you can specify that path using the `--private-key` flag, for example `--private-key=~/.ssh/my_key`.

> At completion, Ansible will display all the public IPs created categorized by `natsserver` and `natsclient` groups.  You may use these public IPs to ssh to any host
and to use the NATS [HTTP monitor](https://docs.nats.io/running-a-nats-service/nats_admin/monitoring) at `http://<natsserver public IP>:8222` as desired.

> Note: All benchmark traffic is routed on private IPs and does not leave the provisioned VPC

## SSHing into the client host

In the [output](https://www.terraform.io/intro/getting-started/outputs.html) produced by Terraform, there's a `client_ssh_host` variable that provides the IP address for the client EC2 host from which benchmarks can be run. You can SSH into that host using this command:

```bash
$ ssh -i ~/.ssh/nats_aws ubuntu@$(terraform output client_ssh_host | tr -d '"')
```

## Running the benchmarks from the client host

Once you've successfully SSHed into the client host, you can run all [available benchmark workloads](../#benchmarking-workloads) like this:

```bash
$ cd /opt/benchmark
$ sudo bin/benchmark --drivers driver-nats-jetstream/nats.yaml workloads/*.yaml
```

You can also run specific workloads in the `workloads` folder. Here's an example:

```bash
$ sudo bin/benchmark --drivers driver-nats-jetstream/nats.yaml workloads/1-topic-16-partitions-1kb.yaml
```

## (Optional) NATS server and JetStream information and built-in benchmark

### NATS server information
On any of the benchmark worker hosts, you may use the NATS CLI to get information about your NATS cluster:

```bash
cd /opt/benchmark; ./nats server ls
```
> You may also leverage the enabled [HTTP monitoring](https://docs.nats.io/running-a-nats-service/nats_admin/monitoring) endpoints at `http://<public IP of provisioned NATS server>:8222`

### JetStream information and management

You may get information about the JetStreams and JS Consumers that are provisioned by your benchmark runs, optionally delete the JetStreams etc.

```bash
cd /opt/benchmark

./nats context select UserA

# summary list
./nats stream ls

# JetStream detail, will prompt...
./nats stream info

# JS Consumer details, will prompt...
./nats consumer info

# delete JetStream (and its related JS Consumers), will prompt...
./nats stream rm
```
### NATS built-in benchmark
The NATS CLI "bench" utility may also be used to do non-JetStream and JetStream tests:

```bash
# On natsclient hosts
cd /opt/benchmark

# Change sub, pub, size, and replicas parameters for desired comparison

./nats context select UserA

# NATS core (w/o JetStream QoS)
./nats bench --sub=1 --size=1024 --msgs 10000000 foo
./nats bench --pub=1 --size=1024 --msgs 10000000 foo

# NATS JetStream with Push JS Consumer
./nats bench --sub=1 --size=1024 --msgs 10000000 --js --storage="file" --replicas=2 foojs
./nats bench --pub=1 --size=1024 --msgs 10000000 --js --storage="file" --replicas=2 foojs

# NATS JetStream with Pull JS Consumer
./nats bench --sub=1 --size=1024 --msgs 10000000 --js --storage="file" --replicas=2 --pull foojs
./nats bench --pub=1 --size=1024 --msgs 10000000 --js --storage="file" --replicas=2 --pull foojs
```

## Cleaning up

Use terraform to deprovision all cloud resources.

```bash
# On your workstation...

$ cd driver-nats-jetstream/deploy
$ terraform destroy
```

When you run `terraform destroy`, you will be prompted to type `yes`. Type `yes` to continue with the de-provisioning.

Once de-provisioning is complete, you will see a confirmation message.